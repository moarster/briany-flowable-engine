# Implementation prompt: backend catch-up for the contract delta

Self-contained task for a coding agent working in `briany-flowable-engine`
(Kotlin 2.3 / Java 25, Spring Boot 4, Flowable 8, Jackson 3 `tools.jackson.*`, Liquibase,
Testcontainers). that is not yet done in this backend. Read it end to end.

Flowable 8 and Spring Boot 4 are new; where a call is marked "verify", confirm the exact API
before relying on it, do not assume.

---

## 0. Verification snapshot (already checked against the code)

The contract (`contract/rest/openapi-v1.yaml`, a git submodule) defines 49 operations. A diff
of every `operationId` against the controllers shows **only two are unimplemented**:
`getEngineCapabilities` and `getBpmnPalette` (the `Platform` tag - no `PlatformController`
exists, so the generated `PlatformApi` defaults return 501). Everything else already has a
controller override.

Status of the delta's "Open items for the backend" (section 5.8) and schema changes:

| Item | Delta ref | Status | In this prompt |
| --- | --- | --- | --- |
| Platform: `getEngineCapabilities`, `getBpmnPalette` | Operations added | NOT done | YES - workstream B |
| CORS configuration | 5.8 #2 | NOT done (`SecurityConfig` has no `cors`) | YES - workstream F |
| Deploy failure detail (`Problem.errors[]`) | 5.8 #3 | NOT done (message-only 409) | YES - workstream D |
| Script/shell exclusion unconditional | 5.8 #5 | PARTIAL (see B/E below) | YES - workstream C |
| `TASK_HAS_NO_FORM` typed problem | 5.8 #6 | NOT done (`checkNotNull` -> `IllegalStateException`) | YES - workstream E |
| `getProcessInstance` implemented on singular tag | 5.8 #1 | DONE (`ProcessInstanceController.getProcessInstance`) | no |
| `ApplicationMapper.deployedResources` populated | 5.8 #4 | DONE for `getApplication(key)`/`findApplication` via `toApplicationWithResources` | optional tidy only |
| `ProcessDefinition.stats` | Schema change | DONE (`ProcessDefinitionStatsService`) | no |
| `ModelerAppRef.stats` / `includeStats` | Schema change | Covered separately | see `MODELER_APP_STATS_PROMPT.md` |
| `Task.processDefinitionKey/Name` | Schema change | DONE (`TaskService`/`TaskMapper`) | no |

Do not touch anything in the "DONE" rows except the optional item-4 tidy in workstream G.
`ModelerApp` stats is out of scope here - it has its own prompt.

## 1. Shared prerequisite - typed API problem (workstream A)

Two workstreams (D deploy errors, E task-no-form) need a `Problem` response carrying a `code`
and/or a structured `errors[]`, at a status chosen at throw time. The current
`GlobalExceptionHandler` (`src/main/kotlin/ru/briany/common/api/GlobalExceptionHandler.kt`)
only maps validation exceptions and never emits `code`/`errors`. Add one small typed
exception plus a handler; both later workstreams reuse it.

The generated model already supports this. `Problem` has `detail` (required), `code`,
`status`, `title`, `type`, `instance`, and `errors: List<ValidationError>`;
`ValidationError` has required `field` and `message`.

NEW `src/main/kotlin/ru/briany/common/api/ApiProblemException.kt`:

```kotlin
package ru.briany.common.api

import org.springframework.http.HttpStatus
import ru.briany.generated.model.ValidationError

/**
 * Carries everything needed to render a contract `Problem`: an HTTP status, an optional stable
 * `code` the client can branch on, and optional structured field errors. Handled by
 * [GlobalExceptionHandler]. Use for typed, client-facing failures (a task with no form, a
 * deploy that fails validation) where a plain message is not enough.
 */
class ApiProblemException(
    val status: HttpStatus,
    val detail: String,
    val code: String? = null,
    val errors: List<ValidationError> = emptyList(),
) : RuntimeException(detail)
```

EDIT `GlobalExceptionHandler.kt` - add a handler that returns `ResponseEntity<Problem>` so the
status is dynamic (the existing handlers keep their static `@ResponseStatus`):

```kotlin
@ExceptionHandler(ApiProblemException::class)
fun handleApiProblem(ex: ApiProblemException): ResponseEntity<Problem> {
    log.warn("API problem [{}]: {}", ex.code ?: ex.status.value(), ex.detail)
    return ResponseEntity.status(ex.status).body(
        Problem(
            detail = ex.detail,
            status = ex.status.value(),
            code = ex.code,
            errors = ex.errors.ifEmpty { null },
        ),
    )
}
```

Add imports: `org.springframework.http.ResponseEntity`, `ru.briany.generated.model.Problem`.
Confirm the generated `Problem` constructor parameter names (`detail`, `status`, `code`,
`errors`) match; adjust if the generator named them differently.

## 2. Platform capabilities and palette (workstream B)

Implements the only two missing operations. Paths (from the contract): `GET
/api/v1/engine-capabilities` -> `EngineCapabilities`, `GET /api/v1/bpmn-palette` ->
`BpmnPalette`. The generated `PlatformApi` interface already declares both.

### B.1 EngineCapabilities

Schema (contract ~2369): required `allowedActivityTypes`, `allowedDelegateBeans`,
`allowedClassPrefixes`; optional `flowableVersion`, `activityWhitelistEnabled`,
`httpTaskEnabled`. `allowedActivityTypes` is snake_case (`user_task`, ...).

Sources already in the codebase:
- `allowedActivityTypes` <- `BpmEngineProperties.whitelist.activities` (a
  `List<BpmnActivityType>`; `BpmnActivityType.name.lowercase()` yields the snake_case form,
  e.g. `USER_TASK` -> `user_task`).
- `activityWhitelistEnabled` <- `BpmEngineProperties.whitelist.enabled`.
- `allowedDelegateBeans` <- `SafeBpmnDeploymentValidator.ALLOWED_DELEGATE_BEANS` (`{kvDelegate}`).
- `allowedClassPrefixes` <- `SafeBpmnDeploymentValidator.ALLOWED_CLASS_PREFIXES`
  (`[ru.briany., org.flowable.]`).
- `httpTaskEnabled` <- derive: `!whitelist.enabled || whitelist.activities.contains(HTTP_SERVICE_TASK)`.
- `flowableVersion` <- `org.flowable.engine.ProcessEngine.VERSION` (verify this constant exists
  in Flowable 8; if not, inject a config value, e.g. `briany.engine.flowable-version`, wired
  from the Gradle version catalog).

NEW `src/main/kotlin/ru/briany/engine/api/EngineCapabilitiesService.kt`:

```kotlin
package ru.briany.engine.api

import org.springframework.stereotype.Service
import ru.briany.engine.config.BpmEngineProperties
import ru.briany.engine.config.BpmnActivityType
import ru.briany.engine.config.validator.SafeBpmnDeploymentValidator
import ru.briany.generated.model.EngineCapabilities

@Service
class EngineCapabilitiesService(
    private val bpmConfig: BpmEngineProperties,
) {
    fun capabilities(): EngineCapabilities {
        val whitelist = bpmConfig.whitelist
        return EngineCapabilities(
            allowedActivityTypes = whitelist.activities.map { it.name.lowercase() },
            allowedDelegateBeans = SafeBpmnDeploymentValidator.ALLOWED_DELEGATE_BEANS.toList(),
            allowedClassPrefixes = SafeBpmnDeploymentValidator.ALLOWED_CLASS_PREFIXES,
            flowableVersion = org.flowable.engine.ProcessEngine.VERSION,
            activityWhitelistEnabled = whitelist.enabled,
            httpTaskEnabled = !whitelist.enabled || whitelist.activities.contains(BpmnActivityType.HTTP_SERVICE_TASK),
        )
    }
}
```

Note the intended threat-model contract from the delta: `EngineCapabilities` must NOT report a
script or shell flag, and must never list `script_task` as allowed. After workstream C removes
`script_task` from the whitelist, `allowedActivityTypes` excludes it automatically. Do not add
`allowedScriptFormats`.

### B.2 BpmnPalette

Schema (contract ~2425): required `elements: BpmnElementDescriptor[]`; an empty array is valid
and means "only the built-in palette". `BpmnElementDescriptor` is defined in
`contract/rest/components/schemas/BpmnElementDescriptor.yaml` and generated as
`ru.briany.generated.model.BpmnElementDescriptor`.

Runtime source of descriptors: `build.gradle.kts:105` adds
`resources.srcDir(layout.buildDirectory.dir("generated/bpmn-descriptors"))`, i.e. a build-time
pipeline can emit descriptor JSON onto the classpath. There are currently no descriptor
sources, so the correct, contract-valid result today is an empty `elements` array.

Implement it to load whatever descriptors are on the classpath and tolerate none:

- Add a config property (e.g. in `application.properties`)
  `briany.bpmn.palette.descriptors-location=classpath*:/bpmn-descriptors/*.json` so the
  location is explicit and adjustable. Confirm/adjust it against the descriptor pipeline's
  actual output path (the `generated/bpmn-descriptors` srcDir maps into the classpath root).
- NEW `BpmnPaletteService`: use `PathMatchingResourcePatternResolver.getResources(location)`,
  deserialize each resource with the injected `tools.jackson.databind.ObjectMapper` into
  `BpmnElementDescriptor`, sort deterministically (by descriptor id), and return
  `BpmnPalette(elements = ...)`. On zero matches, return `BpmnPalette(elements = emptyList())`.
  Do not fail the request when the location has no matches.

Sketch:

```kotlin
@Service
class BpmnPaletteService(
    private val objectMapper: ObjectMapper,
    @Value("\${briany.bpmn.palette.descriptors-location:classpath*:/bpmn-descriptors/*.json}")
    private val location: String,
) {
    private val resolver = PathMatchingResourcePatternResolver()

    fun palette(): BpmnPalette {
        val resources = runCatching { resolver.getResources(location) }.getOrDefault(emptyArray())
        val elements =
            resources
                .filter { it.isReadable }
                .map { res -> res.inputStream.use { objectMapper.readValue(it, BpmnElementDescriptor::class.java) } }
                .sortedBy { it.id }
        return BpmnPalette(elements = elements)
    }
}
```

Confirm the `BpmnElementDescriptor` property used for sorting (`id`) exists; if the schema
names it differently, sort by that. detekt: no `catch (e: Exception)` - `runCatching` here is
acceptable, but if detekt's `TooGenericExceptionCaught` flags it, catch the specific
`IOException` from `getResources` instead.

### B.3 Controller

NEW `src/main/kotlin/ru/briany/engine/api/PlatformController.kt`:

```kotlin
@RestController
class PlatformController(
    private val engineCapabilitiesService: EngineCapabilitiesService,
    private val bpmnPaletteService: BpmnPaletteService,
) : PlatformApi {
    override fun getEngineCapabilities(): ResponseEntity<EngineCapabilities> =
        ResponseEntity.ok(engineCapabilitiesService.capabilities())

    override fun getBpmnPalette(): ResponseEntity<BpmnPalette> =
        ResponseEntity.ok(bpmnPaletteService.palette())
}
```

Confirm the generated interface is named `PlatformApi` and the method signatures match
(no parameters). If the tag generated a different interface name, implement that.

## 3. Unconditional script and shell exclusion (workstream C)

Delta 5.8 #5. Current state: shell is rejected at deploy by `ShellTaskValidator` (always
installed) but the runtime `CustomActivityBehaviorFactory.createShellActivityBehavior` only
disables shell when the whitelist is enabled; script tasks are allowed at deploy for
`scriptFormat in ALLOWED_SCRIPT_FORMATS` (`{groovy}`) and have no runtime block; and
`briany.engine.whitelist.activities` still contains `script_task`. Make all of this
unconditional.

1. **Config** - `src/main/resources/application.properties:26`: remove `script_task` from
   `briany.engine.whitelist.activities`. New value:
   `user_task,service_task,call_activity,sub_process,business_rule_task`. Check
   `application-dev.properties` for an override (currently none) and any test properties.

2. **Deploy-time script rejection** -
   `src/main/kotlin/ru/briany/engine/config/validator/SafeBpmnDeploymentValidator.kt`: change
   `checkScriptTaskLanguage` so every `ScriptTask` is rejected regardless of `scriptFormat`
   (drop the `ALLOWED_SCRIPT_FORMATS` allowance). Keep `SCRIPT_LANGUAGE_ERROR_KEY`. Simplest:
   rename to `rejectScriptTask` and always `addError(...)` with a message like
   `"scriptTask:${task.id}: script tasks are prohibited in this environment"`. Remove the now
   unused `ALLOWED_SCRIPT_FORMATS`.

3. **Runtime shell block unconditional** -
   `src/main/kotlin/ru/briany/engine/config/behavior/CustomActivityBehaviorFactory.kt`: make
   `createShellActivityBehavior` always return `DisabledActivityBehavior(...)` (remove the
   `isAllowed("shell")` branch). `isAllowed` may become unused - remove it if so.

4. **Runtime script block (defense in depth)** - add an override for the script task behavior
   in the same factory returning a disabled behavior. VERIFY the exact Flowable 8 method
   (likely `createScriptTaskActivityBehavior(scriptTask: ScriptTask): ScriptTaskActivityBehavior`).
   Because the return type is specific, `DisabledActivityBehavior` (which extends
   `ShellActivityBehavior`) cannot be returned there. Add a sibling that extends the script
   behavior type and blocks on execute/trigger, e.g.:

   ```kotlin
   class DisabledScriptTaskBehavior(private val reason: String) : ScriptTaskActivityBehavior(/* args per Flowable 8 ctor */) {
       override fun execute(execution: DelegateExecution) { throw SecurityException(reason) }
   }
   ```

   If constructing `ScriptTaskActivityBehavior` in Flowable 8 is awkward, rely on the
   deploy-time rejection (step 2) as the guaranteed control and leave a `// TODO` with the
   verified signature - deploy is blocked, so the runtime path is unreachable in practice.
   State clearly in a comment which control is primary (deploy-time) and which is
   defense-in-depth.

Tests: verify no existing test deploys a `ScriptTask` expecting success. The two test BPMN
files that mention "script" (`deployments/only-native-app/loop_simple.bpmn`,
`deployments/app-with-two-apps/bform_process.bpmn`) use script only in condition/sequence-flow
expressions, not `<scriptTask>`, so they are unaffected - confirm this. Add a negative test:
deploying a process containing a `<scriptTask>` (any format) is rejected.

## 4. Deploy failure detail as `Problem.errors[]` (workstream D)

Delta 5.8 #3. The UI (`DeployFailurePanel.tsx`) maps `Problem.errors[]` onto files and diagram
elements using `field = "<fileKey>"` or `"<fileKey>#<bpmnElementId>"` and `message` = the
engine validation message. Today `ModelerAppDeployService.runDeploy` catches `FlowableException`
and rethrows `ResponseStatusException(409, "Engine deployment failed: <message>")` - a flat
string, no structure.

Produce structured errors by validating before the engine call, using the same
`ProcessValidator` the engine is configured with, then mapping each
`org.flowable.validation.ValidationError` to a contract `ValidationError`:

- `field` = the file's `fileKey`, plus `#<activityId>` when the engine error has an
  `activityId`. Map the engine error's `resourceName` back to the modeler file via the file
  list (`resourceName` is stored on `ModelerAppFileEntity`). When it cannot be mapped, fall
  back to the app key.
- `message` = the engine error's problem/description
  (`ValidationError.problem` / `defaultDescription`).

Implementation notes:
- Expose the configured `ProcessValidator` for reuse. It is currently built inline in
  `BpmnValidatorConfig.bpmnValidatorConfigurer` and set on the engine config. Refactor so the
  `ProcessValidator` (standard set + `ShellTaskValidator` + `SafeBpmnDeploymentValidator`) is a
  Spring `@Bean` that both the engine configurer and a new pre-deploy check consume, so the two
  paths cannot drift.
- In `ModelerAppDeployService`, before `deploymentService.deploy(...)`, parse each non-BFORM
  file to a `BpmnModel` (`org.flowable.bpmn.converter.BpmnXMLConverter`), run
  `processValidator.validate(bpmnModel)`, collect errors, and if any, throw
  `ApiProblemException(HttpStatus.CONFLICT, detail = "Deployment validation failed", code = "DEPLOY_VALIDATION_FAILED", errors = mapped)`.
- Keep the existing `catch (FlowableException)` as a fallback for engine-side failures that
  slip past pre-validation, but wrap them as
  `ApiProblemException(HttpStatus.CONFLICT, detail = ex.message, code = "DEPLOY_FAILED", errors = [ValidationError(field = app.key, message = ex.message)])`
  so the client still gets the documented shape.

Keep functions <= 60 lines; extract the file-to-error mapping into a small helper. VERIFY the
Flowable 8 `ProcessValidator.validate` signature and the `ValidationError` accessors
(`getResourceName`, `getActivityId`, `getProblem`, `getDefaultDescription`).

Tests: extend the modeler IT (or add one) that uploads a BPMN with a disallowed construct
(a `<scriptTask>`, or a `flowable:class` outside the allowed prefixes) and asserts the deploy
returns 409 with `$.errors[0].field` starting with the file key and a non-empty
`$.errors[0].message`.

## 5. `TASK_HAS_NO_FORM` typed problem (workstream E)

Delta 5.8 #6. In `src/main/kotlin/ru/briany/workflow/task/formed/FormedTaskService.kt:82-83`
the missing-form path is `checkNotNull(taskDto.formKey) { ... }`, which throws
`IllegalStateException` (renders as a 500, not the documented shape). Replace with the typed
problem so the form endpoint answers `404` with `code = "TASK_HAS_NO_FORM"`:

```kotlin
val formKey =
    taskDto.formKey
        ?: throw ApiProblemException(
            status = HttpStatus.NOT_FOUND,
            detail = "Task '$taskId' has no form",
            code = "TASK_HAS_NO_FORM",
        )
```

Import `ru.briany.common.api.ApiProblemException` and `org.springframework.http.HttpStatus`
(HttpStatus is already imported). This is the `getFormedTask` operation
(`GET /api/v1/tasks/{id}/form` in the delta's naming). The UI is configured not to retry this
404 and renders the raw variables editor instead - so returning 404 with the code is the
contract, not an error.

Tests: a task without a `formKey` -> `GET /api/v1/tasks/{id}/form` returns 404 and
`$.code == "TASK_HAS_NO_FORM"`.

## 6. CORS (workstream F)

Delta 5.8 #2. `src/main/kotlin/ru/briany/security/SecurityConfig.kt` configures the filter
chain but has no `cors`. Add a configurable allowed-origins list; keep the default empty so
current single-origin (Vite proxy in dev, Traefik single-origin in prod) behavior is
unchanged, and the contract stand / split deployments can opt in.

- Add to `SecurityProperties` (`src/main/kotlin/ru/briany/security/SecurityProperties.kt`) a
  nested `cors` with `allowedOrigins: List<String> = emptyList()` (and optionally
  `allowedMethods`, `allowedHeaders` with sensible defaults `["*"]`).
- In `SecurityConfig`, enable `http.cors { it.configurationSource(corsConfigurationSource()) }`
  and add a `CorsConfigurationSource` bean built from the property. When `allowedOrigins` is
  empty, register no permissive mapping (effectively same-origin only). Use
  `setAllowedOriginPatterns` if you need credentials with wildcards; otherwise
  `setAllowedOrigins`.

```kotlin
@Bean
fun corsConfigurationSource(props: SecurityProperties): CorsConfigurationSource {
    val source = UrlBasedCorsConfigurationSource()
    val origins = props.cors.allowedOrigins
    if (origins.isNotEmpty()) {
        val cfg = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        source.registerCorsConfiguration("/api/**", cfg)
    }
    return source
}
```

Confirm `SecurityConfig` is the right chain (there is also `ActuatorSecurityConfiguration`);
apply CORS to the API chain only. Add the config keys under `briany.security.cors.*` and
document them in `CLAUDE.md`'s auth section if you add environment-facing options.

## 7. Optional tidy - `deployedResources` on secondary getters (workstream G)

Delta 5.8 #4 is already satisfied for the Applications page: `ApplicationService.getApplication(key)`
and `findApplication(key)` go through `toApplicationWithResources` and populate
`deployedResources`. For consistency only (not required by the UI), the version-specific
`getApplication(key, version)` and id-based `getApplication(id)` still call
`ApplicationMapper.from(definition, getAppDeployment(definition))` without resources. If you
want them consistent, route them through a resources-aware helper too. Skip if out of appetite;
it is not a delta blocker.

## 8. Style / detekt (mandatory for all new code)

- No `catch (e: Exception)`, no `!!`, no `println` (SLF4J only).
- Functions <= 60 lines; max line length 140; no magic numbers.
- `Instant`/`LocalDate` only. Constructor injection; `@Service`; `@Transactional(readOnly = true)`
  where a read service touches JPA (Platform services do not touch JPA, so no annotation
  needed).
- New controllers implement the generated `*Api` interface (API-first) - do not add ad-hoc
  `@GetMapping`.
- No contract (`openapi-v1.yaml`) edits and no OpenAPI generator config changes - every schema
  and interface used here already exists in the submodule. If generated sources look stale
  locally, a normal build regenerates them.

## 9. Build / rollout

When approved, a reasonable order:

1. Workstream A (shared exception) - unblocks D and E.
2. Workstream B (Platform) - independent, highest user value (unblocks the modeler palette and
   capabilities gating in the UI).
3. Workstream C (script/shell hardening) - config + validators + behavior factory.
4. Workstreams D, E (structured deploy errors, typed no-form).
5. Workstream F (CORS). Optional G.
6. `./gradlew detekt` then `./gradlew test` (Testcontainers starts PostgreSQL). All green,
   including new negative deploy tests and the untouched suites.

## 10. Acceptance checklist

- [ ] `GET /api/v1/engine-capabilities` returns snake_case `allowedActivityTypes` (no
      `script_task`), `allowedDelegateBeans=[kvDelegate]`, `allowedClassPrefixes=[ru.briany., org.flowable.]`,
      `activityWhitelistEnabled=true`, a `flowableVersion`, and no script/shell flag.
- [ ] `GET /api/v1/bpmn-palette` returns `{ "elements": [] }` today (valid) and any classpath
      descriptors when present; it never 500s on an empty location.
- [ ] Deploying a process with a `<scriptTask>` or a disallowed `flowable:class` is rejected
      with 409 `Problem` whose `errors[].field` is `<fileKey>` or `<fileKey>#<elementId>` and
      `errors[].message` is the engine message.
- [ ] A `<scriptTask>` is rejected at deploy regardless of `scriptFormat`; shell is blocked
      unconditionally at both deploy and runtime; `script_task` is gone from the whitelist.
- [ ] `GET /api/v1/tasks/{id}/form` on a formless task returns 404 with `code=TASK_HAS_NO_FORM`.
- [ ] CORS is off by default and enabled by `briany.security.cors.allowed-origins`.
- [ ] No changes to the contract, codegen config, DB schema, or the DONE items above.
- [ ] detekt clean; `test` green. `ModelerApp` stats remain the separate prompt's concern.
```
