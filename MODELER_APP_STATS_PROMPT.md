# Implementation prompt: ModelerApp `includeStats` (on-the-fly, shared aggregator)

Self-contained task for a coding agent working in `briany-flowable-engine`
(Kotlin 2.3 / Java 25, Spring Boot 4, Flowable 8, Jackson 3, Liquibase, Testcontainers).
Read it end to end before writing code. Everything needed is here; do not re-derive
the decision.

---

## 0. One-paragraph summary

Make `ModelerAppController.listModelerApps` and `getModelerApp` honor the `includeStats`
query parameter, returning the already-contracted `ModelerAppStats` projection
(`processDefinitions`, `decisions`, `forms`, and an optional `instances` sub-object).
Compute it on the fly (no new table, no events), mirroring how
`ProcessDefinitionController` already works. Extract the runtime/history counting into one
shared, reusable component that both the process-definition path and the new modeler path
call, so the counting rules live in exactly one place.

## 1. Decisions already made (do not revisit)

- **On-the-fly, not event-sourced.** No projection table, no Flowable event listeners, no
  reconciliation job, no scheduler. The repository is a showcase/demo; expected volumes and
  request frequency are low, so on-the-fly is sufficient and stays consistent with the
  existing `ProcessDefinition` implementation.
- **Shared per-key aggregator.** Extract the "count instances for a process definition key"
  logic out of `ProcessDefinitionStatsService` into a new
  `ProcessInstanceStatsAggregator`. Both `ProcessDefinitionStatsService` and the new
  `ModelerAppStatsService` call it. This is the single source of truth for the counting
  rules.
- **No API contract change, no codegen change, no DB change.** The `ModelerAppStats` schema
  and the `stats` field on `ModelerAppRef` already exist in `contract/rest/openapi-v1.yaml`,
  so the generated DTOs (`ru.briany.generated.model.ModelerAppStats`, `.ModelerAppRef`,
  `.ModelerApp`) already carry `stats`. You only populate it.

## 2. Non-goals (explicitly out of scope)

- No new database table, no Liquibase changeset, no schema migration.
- No `FlowableEventListener`, no `@Scheduled`, no cache/TTL.
- No changes to `openapi-v1.yaml` or to the OpenAPI generator config.
- No new REST endpoint. No change to any endpoint other than the two named above.
- No frontend changes (the UI already sends `includeStats=true` and renders `stats`).

## 3. Key facts the implementation must respect

### 3.1 `ModelerAppStats` has two parts of very different cost

From `contract/rest/openapi-v1.yaml` (schema `ModelerAppStats`, ~line 2459):

```yaml
ModelerAppStats:
  required: [processDefinitions, decisions, forms]   # file composition, cheap
  properties:
    processDefinitions: integer   # count of BPMN files in the app
    decisions:          integer   # count of DMN files
    forms:              integer   # count of BFORM files
    instances:                    # OPTIONAL - runtime/history counts, the expensive part
      $ref: ProcessInstanceStats  # { running, completed, suspended, total }
```

- **File composition** (`processDefinitions`/`decisions`/`forms`) is required and cheap: it is
  a count of the app's files by type. On the `get` path the file summaries are already
  loaded; on the `list` path fetch only file metadata (no BYTEA content) with one query.
- **`instances`** is optional and is the only expensive part. For a modeler app it is the
  sum, over the app's BPMN process definition keys, of the exact same per-key counts that
  `ProcessDefinitionStatsService` computes today.

### 3.2 An app's process definition keys are its BPMN files' `fileKey`s

`ModelerFileIntrospector.introspectXml` derives a BPMN file's `fileKey` from the `id`
attribute of the single `<process>` element (see `ModelerFileIntrospector.kt:71-76`), i.e.
the process definition key. So the set of process definition keys for a deployed app is
exactly `{ file.fileKey | file.type == BPMN }`. This is the aggregation boundary, and it is
the same rule the UI mock uses.

Caveat to note in a code comment (do not add logic for it): counting is by process
definition key, so if two apps ever declared a BPMN process with the same id they would
share instance counts. In this single-tenant demo keys are unique, and this matches the UI
contract, so key-based counting is correct here.

### 3.3 Frontend contract that must not break

`briany-ui` already consumes this (do not change the UI, just do not break it):

- `stats` lives on `ModelerAppRef`, so both list and detail responses can carry it. Both the
  list route (`applications.index.tsx`) and the detail route
  (`applications.$appKey.index.tsx`) already call with `includeStats: true`.
- `ApplicationTile.tsx` renders the composition counters whenever `stats` is present, and
  renders the instance bar only when `deployed && stats.instances && stats.instances.total > 0`.
- The UI treats an absent/`null` `instances` as "not deployed" and hides the bar. So it is
  correct and expected to return `instances = null` for a non-deployed app.
- Field names and types are fixed: `processDefinitions`/`decisions`/`forms` are integers;
  `instances` is `{ running, completed, suspended, total }` integers.
- When `includeStats` is false (the default), `stats` must be absent/`null` - same behavior
  as `ProcessDefinition` today. Do not fabricate stats on the default path.

## 4. Target architecture

```
ModelerAppController
  |  listModelerApps(includeStats)   getModelerApp(includeStats)
  v
ModelerAppStatsService  (domain/modeler, @Transactional(readOnly = true))
  |    - base data:            ModelerAppService.list / .get  (unchanged)
  |    - file composition:     from file metadata (list: one projection query; get: app.files)
  |    - instances (deployed): ProcessInstanceStatsAggregator.forKeys(bpmnKeys)
  v
ProcessInstanceStatsAggregator  (engine/api, contour 1 - the ACL over Flowable)
       forKey(key) / forKeys(keys)  ->  RuntimeService + HistoryService

ProcessDefinitionStatsService  (engine/api) also calls ProcessInstanceStatsAggregator.forKey
```

Contour note (theme 1 of the article): `ProcessInstanceStatsAggregator` is the contour-1
anti-corruption boundary over Flowable's Runtime/History services. `domain/modeler`
consuming it is consistent with the modeler already being the acknowledged "does not fit the
three contours" feature (it already depends on `workflow.application`, contour 2). State this
rationale in a KDoc comment so the architecture stays honest.

## 5. Changes, file by file

### 5.1 NEW - `src/main/kotlin/ru/briany/engine/api/ProcessInstanceStatsAggregator.kt`

Moves the counting rules out of `ProcessDefinitionStatsService` unchanged, and adds a
multi-key sum. Uses only single-key Flowable query APIs that the codebase already relies on.

```kotlin
package ru.briany.engine.api

import org.flowable.engine.HistoryService
import org.flowable.engine.RuntimeService
import org.springframework.stereotype.Service
import ru.briany.generated.model.ProcessInstanceStats

/**
 * Single source of runtime/history instance counts, keyed by process definition key.
 *
 * Contour-1 anti-corruption boundary over Flowable's Runtime/History services. Both
 * [ProcessDefinitionStatsService] (one key) and the modeler stats path (sum over an app's
 * BPMN process keys) consume it, so the counting rules live in exactly one place.
 */
@Service
class ProcessInstanceStatsAggregator(
    private val runtimeService: RuntimeService,
    private val historyService: HistoryService,
) {
    /** Counts for a single process definition key. */
    fun forKey(key: String): ProcessInstanceStats {
        val historic = { historyService.createHistoricProcessInstanceQuery().processDefinitionKey(key) }
        val total = historic().count()
        val completed = historic().finished().count()
        val unfinished = historic().unfinished().count()
        val suspended = runtimeService.createProcessInstanceQuery().processDefinitionKey(key).suspended().count()
        val running = (unfinished - suspended).coerceAtLeast(0)
        return ProcessInstanceStats(
            running = running.toInt(),
            completed = completed.toInt(),
            suspended = suspended.toInt(),
            total = total.toInt(),
        )
    }

    /**
     * Counts summed over a set of process definition keys. One modeler app can bundle several
     * BPMN processes; keys within an app are unique and an instance has exactly one definition
     * key, so per-key counts are additive.
     */
    fun forKeys(keys: Collection<String>): ProcessInstanceStats =
        keys.distinct().map(::forKey).fold(EMPTY, ::sum)

    private fun sum(
        a: ProcessInstanceStats,
        b: ProcessInstanceStats,
    ): ProcessInstanceStats =
        ProcessInstanceStats(
            running = a.running + b.running,
            completed = a.completed + b.completed,
            suspended = a.suspended + b.suspended,
            total = a.total + b.total,
        )

    companion object {
        val EMPTY = ProcessInstanceStats(running = 0, completed = 0, suspended = 0, total = 0)
    }
}
```

Optional micro-optimization (only if you verify the API exists on Flowable 8; otherwise keep
the safe per-key loop above): replace the loop in `forKeys` with a single query using
`historyService.createHistoricProcessInstanceQuery().processDefinitionKeyIn(keys)` for
total/finished/unfinished, and sum per-key for the runtime `suspended` count if
`ProcessInstanceQuery` lacks a `processDefinitionKeyIn`. Do not assume it exists; the safe
per-key version is the accepted default at demo scale.

### 5.2 EDIT - `src/main/kotlin/ru/briany/engine/api/ProcessDefinitionStatsService.kt`

Behavior-preserving refactor: drop `RuntimeService`/`HistoryService` and the private
`statsFor`, depend on the aggregator instead. Public methods and output are unchanged.

```kotlin
package ru.briany.engine.api

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage

@Service
class ProcessDefinitionStatsService(
    private val processDefinitionFacade: ProcessDefinitionFacade,
    private val statsAggregator: ProcessInstanceStatsAggregator,
) {
    fun getProcess(
        key: String,
        includeStats: Boolean,
    ): ProcessDefinition {
        val definition = processDefinitionFacade.getProcessDefinitionByKey(key)
        return if (includeStats) definition.copy(stats = statsAggregator.forKey(key)) else definition
    }

    fun listProcesses(
        includeStats: Boolean,
        pageable: Pageable,
    ): ProcessDefinitionPage {
        val page =
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.Latest,
                appId = null,
                key = null,
                pageable = pageable,
            )
        if (!includeStats) return page
        return page.copy(data = page.data.map { it.copy(stats = statsAggregator.forKey(it.key)) })
    }
}
```

### 5.3 EDIT - `src/main/kotlin/ru/briany/domain/modeler/ModelerAppFileRepository.kt`

Add a lightweight metadata projection (NOTE: it must NOT select `content`; that column is
BYTEA and eager on the entity, so never load entities just to count). Add the query and a
projection interface.

```kotlin
package ru.briany.domain.modeler

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.briany.generated.model.ModelerFileType
import java.util.UUID

interface ModelerAppFileRepository : JpaRepository<ModelerAppFileEntity, UUID> {
    fun findByAppId(appId: UUID): List<ModelerAppFileEntity>

    fun findByAppIdAndFileKey(
        appId: UUID,
        fileKey: String,
    ): ModelerAppFileEntity?

    @Query(
        "select f.appId as appId, f.type as type, f.fileKey as fileKey " +
            "from ModelerAppFileEntity f where f.appId in :appIds",
    )
    fun findMetaByAppIdIn(
        @Param("appIds") appIds: Collection<UUID>,
    ): List<AppFileMeta>
}

/** Closed projection: file metadata only, no content blob. */
interface AppFileMeta {
    val appId: UUID
    val type: ModelerFileType
    val fileKey: String
}
```

`type` is stored via `ModelerFileTypeConverter` (JPA `AttributeConverter`); Hibernate applies
the converter when reading the projected attribute, so `type` maps back to `ModelerFileType`.
If the interface projection ever fails to map the converted enum, fall back to projecting the
raw string and converting in the service - but try the clean version first.

### 5.4 NEW - `src/main/kotlin/ru/briany/domain/modeler/ModelerAppStatsService.kt`

Mirrors `ProcessDefinitionStatsService`: base data from `ModelerAppService`, stats are the
opt-in extra. One `buildStats` helper serves both paths (keep functions <= 60 lines).

```kotlin
package ru.briany.domain.modeler

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.briany.engine.api.ProcessInstanceStatsAggregator
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppPage
import ru.briany.generated.model.ModelerAppStats
import ru.briany.generated.model.ModelerFileType

/**
 * Enriches modeler apps with the opt-in `stats` projection. File composition is cheap
 * (metadata only); instance counts reuse the shared [ProcessInstanceStatsAggregator] and are
 * computed only for deployed apps. Kept separate from [ModelerAppService] so the base CRUD
 * path stays free of engine/runtime concerns - same split as ProcessDefinitionStatsService.
 */
@Service
@Transactional(readOnly = true)
class ModelerAppStatsService(
    private val modelerAppService: ModelerAppService,
    private val fileRepository: ModelerAppFileRepository,
    private val statsAggregator: ProcessInstanceStatsAggregator,
) {
    fun list(
        filter: ModelerStateFilter,
        pageable: Pageable,
        includeStats: Boolean,
    ): ModelerAppPage {
        val page = modelerAppService.list(filter, pageable)
        if (!includeStats || page.data.isEmpty()) return page
        val metaByApp = fileRepository.findMetaByAppIdIn(page.data.map { it.id }).groupBy { it.appId }
        return page.copy(
            data =
                page.data.map { ref ->
                    val meta = metaByApp[ref.id].orEmpty()
                    ref.copy(
                        stats =
                            buildStats(
                                types = meta.map { it.type },
                                bpmnKeys = meta.filter { it.type == ModelerFileType.BPMN }.map { it.fileKey },
                                deployedVersion = ref.deployedVersion,
                            ),
                    )
                },
        )
    }

    fun get(
        key: String,
        includeStats: Boolean,
    ): ModelerApp {
        val app = modelerAppService.get(key)
        if (!includeStats) return app
        return app.copy(
            stats =
                buildStats(
                    types = app.files.map { it.type },
                    bpmnKeys = app.files.filter { it.type == ModelerFileType.BPMN }.map { it.fileKey },
                    deployedVersion = app.deployedVersion,
                ),
        )
    }

    private fun buildStats(
        types: List<ModelerFileType>,
        bpmnKeys: List<String>,
        deployedVersion: Int?,
    ): ModelerAppStats {
        val byType = types.groupingBy { it }.eachCount()
        return ModelerAppStats(
            processDefinitions = byType[ModelerFileType.BPMN] ?: 0,
            decisions = byType[ModelerFileType.DMN] ?: 0,
            forms = byType[ModelerFileType.BFORM] ?: 0,
            instances = if (deployedVersion == null) null else statsAggregator.forKeys(bpmnKeys),
        )
    }
}
```

Notes:
- Gate `instances` on `deployedVersion != null` to match the UI (which keys the instance bar
  off the deployed state). A deployed app with zero instances returns zeros, and the UI hides
  the bar at `total == 0`.
- `ModelerAppRef.id` and `ModelerApp.id` are `UUID` (see `ModelerAppMapper`); the repository
  parameter is `Collection<UUID>`. `ModelerApp.files` are `ModelerAppFileSummary` DTOs
  carrying `type` and `fileKey`, so the `get` path needs no extra query.
- Enrich via `.copy(...)`, exactly like `ProcessDefinitionStatsService` does for
  `ProcessDefinition`/`ProcessDefinitionPage`.

### 5.5 EDIT - `src/main/kotlin/ru/briany/domain/modeler/ModelerAppController.kt`

Inject `ModelerAppStatsService` and route only the two `includeStats`-bearing methods through
it. Everything else stays on `modelerAppService` / `modelerAppDeployService`.

```kotlin
@RestController
class ModelerAppController(
    private val modelerAppService: ModelerAppService,
    private val modelerAppDeployService: ModelerAppDeployService,
    private val modelerAppStatsService: ModelerAppStatsService,
) : ModelerAppApi {
    override fun listModelerApps(
        includeStats: Boolean,
        state: ModelerAppState?,
        pageable: Pageable,
    ): ResponseEntity<ModelerAppPage> =
        ResponseEntity.ok(modelerAppStatsService.list(ModelerStateFilter.of(state), pageable, includeStats))

    override fun getModelerApp(
        key: String,
        includeStats: Boolean,
    ): ResponseEntity<ModelerApp> = ResponseEntity.ok(modelerAppStatsService.get(key, includeStats))

    // ... all other methods unchanged ...
}
```

No change to `ModelerAppService` or `ModelerAppMapper`.

## 6. Serialization of `null` stats

When `includeStats=false`, `stats` stays at its generated default (`null`), same as the
existing `ProcessDefinition` path - so no change is needed and the UI already handles it. If
the project's Jackson is configured `NON_NULL`, the field is omitted; otherwise it serializes
as `"stats": null`. Both are UI-safe (`app.stats` is falsy either way). Do not add a custom
`@JsonInclude` just for this; match existing behavior.

## 7. Style / detekt (mandatory for new code)

- No `catch (e: Exception)`, no `!!`, no `println` (use SLF4J if you must log).
- Functions <= 60 lines; max line length 140.
- No magic numbers (the literal `0` defaults are fine - detekt ignores 0/1 by default).
- Use `Instant`/`LocalDate`, never `java.util.Date` (not needed here).
- Constructor injection, `@Service`, `@Transactional(readOnly = true)` on read services -
  match the surrounding code.

## 8. Testing

### 8.1 Integration test - extend `ModelerAppControllerIT`

`src/test/kotlin/ru/briany/integration/domain/modeler/ModelerAppControllerIT.kt` is an ordered
IT (`BaseOrderedControllerIT`) that already: creates app `modelerAppIT`, uploads
`process-a.bpmn` (process key `modelerProcessA`), `decision-a.dmn`, `form-a.bform`, deploys at
`@Order(20)`, redeploys at `@Order(40)`, deletes at `@Order(60)`. Add stats assertions
without disturbing the existing order.

Add, around `@Order(45)` (after redeploy, before the delete):

1. Autowire `RuntimeService` (add `@Autowired lateinit var runtimeService: RuntimeService` if
   the base class does not already expose one). Start one instance:
   `runtimeService.startProcessInstanceByKey("modelerProcessA")`.
2. `GET /api/v1/modeler-apps/modelerAppIT?includeStats=true` asserts:
   - `$.stats.processDefinitions` == 1, `$.stats.decisions` == 1, `$.stats.forms` == 1
   - `$.stats.instances.total` == 1
   - For `running` vs `completed`: inspect `src/test/resources/modeler/process-a.bpmn`. If it
     has a wait state (user task / receive), assert `$.stats.instances.running` == 1; if it
     runs to completion synchronously, assert `$.stats.instances.completed` == 1. Assert
     `total` == 1 regardless (that assertion is shape-independent).
3. `GET /api/v1/modeler-apps/modelerAppIT` (no `includeStats`) asserts `$.stats` does not
   exist: `jsonPath("$.stats").doesNotExist()`.
4. `GET /api/v1/modeler-apps?includeStats=true` asserts the app in `$.data` carries
   `$.data[0].stats.processDefinitions` == 1 (and `.instances.total` == 1).
5. `GET /api/v1/modeler-apps` (no `includeStats`) asserts `$.data[0].stats` does not exist.

Also worth a cheap early assertion: on the freshly created draft app (before deploy), request
with `includeStats=true` and assert composition is present but `instances` is null/absent
(not deployed) - e.g. after `@Order(10)` uploads, `GET ...?includeStats=true` ->
`$.stats.processDefinitions` == 1 and `jsonPath("$.stats.instances").doesNotExist()`.

Watch the `TRUNCATE ... RESTART IDENTITY CASCADE` at the start; keep new tests within the
same lifecycle so they see the deployed app.

### 8.2 Regression - `ProcessDefinitionControllerIT`

The `ProcessDefinitionStatsService` change is behavior-preserving, so the existing IT must
stay green unchanged. The current IT does not assert stats at all; optionally add one
assertion for safety: start an instance of `loop_simple`, then
`GET /api/v1/processes/loop_simple?includeStats=true` and assert `$.stats.total` >= 1. Keep it
optional and do not reorder existing tests.

### 8.3 Optional unit test

A focused unit test of `ProcessInstanceStatsAggregator.forKeys` summation is possible with
mocked `RuntimeService`/`HistoryService` query chains, but the chain stubbing is verbose. The
IT above already exercises the real aggregation end to end, so a unit test is optional; if you
add one, mock `forKey` behavior by stubbing the two query builders and assert that `forKeys`
of two keys returns the element-wise sum and that empty keys return `EMPTY`.

## 9. Build / rollout order

Do not run Gradle without the user's confirmation (project rule). When approved:

1. Add `ProcessInstanceStatsAggregator`, refactor `ProcessDefinitionStatsService`.
2. Add the repository projection, `ModelerAppStatsService`, wire the controller.
3. `./gradlew detekt` - fix any findings in new code.
4. `./gradlew test` (Testcontainers spins up PostgreSQL) - all green, including the new
   `ModelerAppControllerIT` assertions and the untouched `ProcessDefinitionControllerIT`.
5. No `openApiGenerate` change and no Liquibase change are expected; if generated sources are
   stale locally, a normal build regenerates them from the unchanged contract.

## 10. Acceptance checklist

- [ ] `GET /api/v1/modeler-apps/{key}?includeStats=true` returns `stats` with correct file
      composition, and `instances` present (deployed) or null (not deployed).
- [ ] `GET /api/v1/modeler-apps?includeStats=true` returns `stats` per item.
- [ ] Both endpoints omit `stats` when `includeStats` is false/absent.
- [ ] `instances` for an app equals the sum over its BPMN `fileKey`s of the same counts
      `ProcessDefinition` reports per key.
- [ ] `ProcessInstanceStatsAggregator` is the only place that talks to `RuntimeService` /
      `HistoryService` for these counts; `ProcessDefinitionStatsService` delegates to it and is
      otherwise behavior-identical.
- [ ] `list` stats path issues one metadata query for the page and never loads file `content`.
- [ ] No contract, codegen, DB, or event-listener changes. No UI changes.
- [ ] detekt clean; `test` green.
