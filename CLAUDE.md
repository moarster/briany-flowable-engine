# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this repository is

A self-contained showcase BPM engine (Kotlin / Spring Boot 4 / Flowable 8) plus a
companion series of Russian-language articles in `article/`. The codebase is the
deliverable, not a scaffold: it demonstrates a small set of reusable patterns worth
writing about, on a neutral, domain-agnostic demo domain.

## What the codebase demonstrates (the three article themes)

1. **Contour architecture** - Ports & Adapters / Anti-Corruption Layer reframed for an
   *embedded* process engine. Contour 1 = engine-coupled code (`engine/api` REST adapters,
   `engine/config` engine wiring + deploy validators, and the runtime EL extensions in
   `bpmn/`); contour 2 = application/orchestration (`workflow/`: applications, tasks, and the
   platform capabilities/palette endpoints); contour 3 = fully-owned domain (`domain/`: forms,
   modeler). Honest twist: `domain/modeler` deliberately reaches into contour 2 (it drives
   `workflow` deployment), and `security/`, `common/`, `config/`, `utils/` are cross-cutting
   infrastructure outside the contour model.
2. **Low-code on headless Flowable 8** - compensating for the removed v8 UI: the runtime
   platform endpoints (`workflow/PlatformController`) that publish the custom palette
   (`getBpmnPalette`, deserializing `BpmnElementDescriptor` descriptors found on the classpath
   - empty until descriptors are added) and the engine's self-description
   (`getEngineCapabilities`, so a client offers only what deploy validation will accept);
   declarative forms with FEEL variable binding (`domain/form/`); and the modeler workspace
   (`domain/modeler/`) that bundles and deploys BPMN/DMN/BFORM to the engine. One bpmn-js demo
   frontend (`briany-ui`).
3. **Security of an embedded engine as a plugin system with a threat model** - defense in
   depth across engine layers: JUEL sandbox escape (`bpmn/security/SafeBeanELResolver`); script
   and shell tasks prohibited **unconditionally**, both at deploy time
   (`engine/config/validator/ShellTaskValidator` and `SafeBpmnDeploymentValidator.rejectScriptTask`)
   and at runtime (`engine/config/behavior/CustomActivityBehaviorFactory` returning
   `DisabledActivityBehavior` / `DisabledScriptTaskBehavior`); an activity-type whitelist
   (`briany.engine.whitelist.*`), a `flowable:class` prefix allowlist and a `delegateExpression`
   bean allowlist (`SafeBpmnDeploymentValidator.ALLOWED_CLASS_PREFIXES` / `ALLOWED_DELEGATE_BEANS`).
   `getEngineCapabilities` surfaces exactly these constraints to clients and never advertises a
   script or shell capability.

The API-first contract and the Testcontainers integration-test platform are strong but
NOT Flowable-specific; they are background infrastructure here.

## Tech stack

Kotlin 2.3 + Java 25, Gradle 9 (Kotlin DSL + version catalog), Spring Boot 4,
Flowable BPM 8.0, Jackson 3 (`tools.jackson.*`, not `com.fasterxml.jackson.*`),
Springdoc + OpenAPI Generator 7.21 (API-first), PostgreSQL, Testcontainers 2.
Flowable 8 and Spring Boot 4 are both new - verify bleeding-edge APIs, don't assume.

## Layout

- `src/main/kotlin/ru/briany/` - application code, organised by contour (see themes above).
- `article/index.ru.md` - the article series (Russian).
- `contract/` - the OpenAPI contract as a git submodule. `contract/rest/openapi-v1.yaml` is the
  source of truth for codegen; `contract/rest/components/schemas/*.yaml` holds the
  `BpmnElementDescriptor` / `InputUiComponent` palette schemas.
- Palette descriptors: `getBpmnPalette` serves descriptor JSON matching
  `briany.bpmn.palette.descriptors-location` (default `classpath*:/bpmn-descriptors/*.json`).
  None ship yet, so the palette is empty by default (a valid response).
- `docker-compose.yml` - postgres + app, auth strategy `flowable` (HTTP Basic vs engine IDM).
- `docker-compose.jwks.yml` - override adding Keycloak + the `jwks` auth strategy;
  realm import lives in `keycloak/bpm-realm.json`.
- `Dockerfile` - copies a prebuilt `build/libs/*.jar` (run `./gradlew bootJar` first).

## Auth strategies

Selected by `briany.security.auth.strategy` (env `BRIANY_SECURITY_AUTH_STRATEGY`).
Each strategy is a `@ConditionalOnProperty` bean in `security/`:
- `flowable` (`FlowableAuthStrategy`) - HTTP Basic checked against the engine's built-in
  IDM (`ACT_ID_USER`). Default; no external dependency.
- `jwks` (`JwksStrategy`) - Bearer tokens validated against a JWKS issuer (Keycloak in
  the demo). Config under `briany.security.auth.jwks.*`.

CORS for the `/api/**` chain is off by default and opt-in via `briany.security.cors.*`:
`allowed-origins` (empty = same-origin only), `allowed-methods`, `allowed-headers`. When
`allowed-origins` is non-empty the chain registers a `/api/**` mapping with credentials
enabled (using `allowedOriginPatterns`, so wildcards stay valid).

## API contract (`contract/rest/openapi-v1.yaml`)

Source of truth for the REST API. `openApiGenerate` (kotlin-spring, `interfaceOnly`)
generates `*Api` interfaces into `build/generated/openapi`; controllers implement them.
Descriptions are in English; anchor every description in official docs, don't invent.

- **Tag casing gates codegen:** singular tag (e.g. `Application`) is generated; plural
  (e.g. `Applications`) is filtered out via `globalProperties.apis`.
- **Vendor extensions:** `x-spring-paginated` (built-in,
  generates `Pageable`). Document any new extension here.
- **Implemented tags:** `Application`, `ProcessDefinition`, `ProcessInstance`, `Task`,
  `Identity` (`GET /me`, in `security/UserController`), `Platform`
  (`engine-capabilities` + `bpmn-palette`, in `workflow/PlatformController`), `Form`,
  `ModelerApp`. Every contract `operationId` has a controller override.
- **Opt-in stats:** `listProcesses` / `getProcess` / `listModelerApps` / `getModelerApp`
  take `?includeStats=true` and fill the `stats` projection. Instance counts come from the
  shared `engine/api/ProcessInstanceStatsAggregator` (per key / sum over keys);
  `domain/modeler/ModelerAppStatsService` adds file composition. Default listings stay cheap.
- **Typed failures:** `common/api/ApiProblemException` renders the contract `Problem` with a
  stable `code` and structured `errors[]` (via `GlobalExceptionHandler`). Deploy validation
  failures map each engine error to `errors[].field = "<fileKey>#<elementId>"`; a formless task
  answers `404` with `code = TASK_HAS_NO_FORM`.

## Conventions

- **Language:** articles itself in **Russian**. English for everything else
  `CLAUDE.md`, `MEMORY.md`, and code comments/identifiers.
- **No typographic symbols** in plain-text / Markdown output: straight quotes ("),
  apostrophe ('), hyphen (-), "->", "|". No em/en dashes, curly quotes, arrows, ellipsis.
  (Rendered website copy and doc/docx/pdf are exempt.). The article is the exception!
- No "for dummies" doc sections (Quick Start, Troubleshooting, Contributing, License).
- Use `Instant` / `LocalDate` / `LocalDateTime`, never `java.util.Date`.
- **detekt compliance is mandatory** for all new code from the first iteration:
  no `catch(e: Exception)`, no `!!`, no `println` (use SLF4J), no magic numbers,
  functions <= 60 lines, max line length 140.
- New controllers implement a generated `*Api` interface (API-first).
- Don't run gradle without user confirmation.
