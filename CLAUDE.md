# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this repository is

A self-contained showcase BPM engine (Kotlin / Spring Boot 4 / Flowable 8) plus a
companion series of Russian-language articles in `article/`. The codebase is the
deliverable, not a scaffold: it demonstrates a small set of reusable patterns worth
writing about, on a neutral, domain-agnostic demo domain.

## What the codebase demonstrates (the three article themes)

1. **Contour architecture** - Ports & Adapters / Anti-Corruption Layer reframed for an
   *embedded* process engine. Contour 1 = engine-coupled adapter (`engine/api`,
   `engine/query`), contour 2 = application/orchestration (`workflow/`), contour 3 =
   fully-owned domain (`domain/`). Honest twist: standalone features (`designer/`) that
   deliberately do NOT fit the three contours.
2. **Low-code on headless Flowable 8** - compensating for the removed v8 UI: the
   build-time descriptor pipeline + palette (`designer/`, `bpmn-descriptors/`),
   declarative forms with FEEL variable binding (`domain/form/`), and the KV-store
   (`domain/kv/`) as a proof-of-concept for the "any capability -> descriptor -> palette
   element without a backend release" conveyor. One bpmn-js demo frontend.
3. **Security of an embedded engine as a plugin system with a threat model** - defense
   in depth across engine layers: JUEL sandbox escape (`bpmn/security/SafeBeanELResolver`),
   deploy-time activity whitelist (`engine/config/behavior/CustomActivityBehaviorFactory`
   + `DisabledActivityBehavior`), BPMN deployment validation (`engine/config/validator/`),
   delegate allowlist (`SafeBpmnDeploymentValidator.ALLOWED_DELEGATE_BEANS`).

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
- `contract/rest` - the OpenAPI contract: `openapi-v1.yaml` (source of truth for codegen) plus
  `components/schemas/*.yaml` (BPMN palette schemas consumed by the descriptor task).
- `bpmn-descriptors/` - JSON descriptors + the `BpmnElementDescriptor` schema feeding
  the palette (theme 2).
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

## API contract (`contract/rest/openapi-v1.yaml`)

Source of truth for the REST API. `openApiGenerate` (kotlin-spring, `interfaceOnly`)
generates `*Api` interfaces into `build/generated/openapi`; controllers implement them.
Descriptions are in English; anchor every description in official docs, don't invent.

- **Tag casing gates codegen:** singular tag (e.g. `Application`) is generated; plural
  (e.g. `Applications`) is filtered out via `globalProperties.apis`.
- **Vendor extensions:** `x-spring-paginated` (built-in,
  generates `Pageable`). Document any new extension here.

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
