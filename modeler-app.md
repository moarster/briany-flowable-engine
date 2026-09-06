# Промпт: доменная сущность ModelerApp (BPMN-конструктор поверх Flowable 8)

## 0. Контекст и цель

Реализовать в проекте `briany-flowable-engine` облегченный аналог Camunda Web
Modeler: серверное хранилище исходников BPMN/DMN/bform без немедленного деплоя, с
явной операцией деплоя в движок Flowable и отслеживанием состояния синхронизации
(draft/synced/ahead). Реализация должна строго следовать существующим паттернам
проекта.

Эталоны для копирования паттернов:

- Персистентность: `src/main/kotlin/ru/briany/domain/form/*` (`FormEntity`,
  `FormRepository`, `FormService`, `FormController`, `FormSchemaConverter`) +
  Liquibase `src/main/resources/db/changelog/changeset/0001_create_brn_form_definition.sql`.
- Интеграция с движком: `src/main/kotlin/ru/briany/workflow/application/*`
  (`DeploymentService`, `ApplicationService`, `ApplicationMapper`, `ApplicationController`).
- API-first: `api/openapi-v1.yaml` -> `openApiGenerate` -> `*Api` -> контроллер
  имплементит интерфейс.
- Ошибки: `Problem` (RFC7807) + `ru.briany.common.api.GlobalExceptionHandler`; в
  сервисах кидать `ResponseStatusException`.
- Пагинация: `ru.briany.common.api.dtos.PagedList` + `x-kotlin-implements` на
  `*Page`-схемах.

## Зафиксированные решения

| Развилка | Решение |
|---|---|
| Именование | `modeler-apps` (одна l), тег `ModelerApp`, база `/api/v1/modeler-apps` |
| Деплой | `POST /{key}/deploy` |
| DELETE | каскадный undeploy через `DeploymentService.delete()` |
| Состояния | по SHA-256 контента |
| Синхронизация | контент - источник истины, поля-колонки read-only проекция |
| GET content | сырые байты + метаданные отдельным JSON |
| Деривация key | строго один process/decision на файл (иначе ошибка валидации) |
| Версионирование | мутабельный workspace, храним только ссылку на задеплоенную версию Flowable |

Дефолты: README.md - markdown-атрибут `readme` (TEXT) на контейнере; лог ошибок -
JSONB-массив `{severity, code, message, at}`; оптимистическая блокировка `@Version`;
`created_at/updated_at`; `tenant_id` default `''` (в API не выставляется, для
единообразия с `FormEntity`).

## 1. Пакет и новые классы

Пакет `ru.briany.domain.modeler`:

- `ModelerAppEntity`, `ModelerAppFileEntity`
- `ModelerAppRepository`, `ModelerAppFileRepository`
- `ModelerAppService` (CRUD контейнера и файлов, деривация, пересчет состояний,
  валидация, лог ошибок)
- `ModelerAppDeployService` (обертка над `DeploymentService`: сбор файлов, синтез
  `.app`, деплой, линковка ресурсов, undeploy)
- `ModelerAppMapper` (entity -> DTO)
- `ModelerAppController implements ModelerAppApi`
- `ModelerFileErrorConverter` (JSONB AttributeConverter для лога ошибок, по образцу
  `FormSchemaConverter`)
- `ModelerFileIntrospector` (bpmn/dmn/bform -> key/name, строгая валидация
  количества элементов)

## 2. Доменная модель (JPA + Liquibase, PostgreSQL)

Миграция: новый changeset `0002_create_brn_modeler_app.sql` + запись в
`db.changelog-master.yaml` с `preConditions` (`onFail: MARK_RAN`,
`not tableExists`), как в 0001.

### 2.1 Таблица `brn_modeler_app` (ModelerAppEntity)

| Колонка | Тип | Ограничения | Назначение |
|---|---|---|---|
| id | UUID | PK | |
| key | VARCHAR(255) | NOT NULL | ключ конструктора, из него синтезируется `.app` key |
| name | VARCHAR(255) | | |
| description | VARCHAR(4000) | | |
| readme | TEXT | nullable | README.md как атрибут контейнера (не файл) |
| state | VARCHAR(16) | NOT NULL default 'draft' | draft/synced/ahead, пересчитывается |
| app_definition_id | VARCHAR(64) | nullable | Flowable AppDefinition id (после 1-го деплоя) |
| app_definition_key | VARCHAR(255) | nullable | фактический key задеплоенного app |
| deployment_id | VARCHAR(64) | nullable | Flowable app deployment id |
| deployed_version | INTEGER | nullable | версия AppDefinition |
| deployed_content_hash | VARCHAR(64) | nullable | агрегатный SHA-256 состава на момент деплоя (см. 5) |
| deployed_at | TIMESTAMPTZ | nullable | |
| tenant_id | VARCHAR(255) | NOT NULL default '' | не выставляется в API |
| lock_version | BIGINT | NOT NULL default 0 | `@Version` |
| created_at | TIMESTAMPTZ | NOT NULL default now() | |
| updated_at | TIMESTAMPTZ | NOT NULL default now() | |

Ограничения: `UNIQUE(key, tenant_id)` (имя `brn_uniq_modeler_app`), индекс по `state`.

### 2.2 Таблица `brn_modeler_app_file` (ModelerAppFileEntity)

| Колонка | Тип | Ограничения | Назначение |
|---|---|---|---|
| id | UUID | PK | |
| app_id | UUID | NOT NULL, FK -> brn_modeler_app(id) ON DELETE CASCADE | |
| file_key | VARCHAR(255) | NOT NULL | деривируется из контента (process/decision/form id) |
| name | VARCHAR(255) | | проекция из контента |
| description | VARCHAR(4000) | | проекция из контента (если есть) |
| type | VARCHAR(16) | NOT NULL | bpmn/dmn/bform |
| resource_name | VARCHAR(4000) | NOT NULL | имя ресурса (filename с расширением) для деплоя |
| content | BYTEA | NOT NULL | blob-исходник (источник истины) |
| content_hash | VARCHAR(64) | NOT NULL | SHA-256 текущего контента |
| deployed_hash | VARCHAR(64) | nullable | SHA-256 контента на момент последнего успешного деплоя |
| state | VARCHAR(16) | NOT NULL default 'draft' | draft/synced/ahead |
| engine_resource_id | VARCHAR(255) | nullable | id процесса/decision/form в движке (после деплоя) |
| error_log | JSONB | nullable | массив ModelerFileError |
| lock_version | BIGINT | NOT NULL default 0 | `@Version` |
| created_at | TIMESTAMPTZ | NOT NULL default now() | |
| updated_at | TIMESTAMPTZ | NOT NULL default now() | |

Ограничения: `UNIQUE(app_id, file_key)` (имя `brn_uniq_modeler_file`), индекс по `app_id`.

Entity-детали (как в `FormEntity`): JSONB через
`@Convert(converter = ModelerFileErrorConverter::class)` +
`@ColumnTransformer(write = "?::jsonb")` + `columnDefinition = "jsonb"`; blob как
`bytea`; `Instant` для времен; `@Version` на `lock_version`. Enum'ы хранить
строками (lowercase) - маппинг в API-enum.

## 3. REST-контракт (добавить в `api/openapi-v1.yaml`)

Тег `ModelerApp` (единственный -> генерится). База `/api/v1/modeler-apps`. Все операции:
`operationId` + ровно один тег + per-op `summary` + описания
на английском. Списки помечать `x-spring-paginated: true`.

| Метод и путь | operationId | Тело/параметры | Ответы |
|---|---|---|---|
| GET `/modeler-apps` | listModelerApps | page/size/sort, опц. `state` | 200 ModelerAppPage; 401/403 |
| POST `/modeler-apps` | createModelerApp | CreateModelerAppRequest | 201 ModelerApp; 400; 409 (дубль key) |
| GET `/modeler-apps/{key}` | getModelerApp | Key | 200 ModelerApp; 404 |
| PUT `/modeler-apps/{key}` | updateModelerApp | UpdateModelerAppRequest (name/description/readme; key неизменяем) | 200 ModelerApp; 400; 404 |
| DELETE `/modeler-apps/{key}` | deleteModelerApp | Key | 204 (каскадный undeploy); 404 |
| POST `/modeler-apps/{key}/deploy` | deployModelerApp | Key, без тела | 200 ModelerApp (с embedded deployedApplication); 400 (нет файлов/ошибки валидации); 404; 409 (ошибка деплоя движка) |
| GET `/modeler-apps/{key}/files` | listModelerAppFiles | Key | 200 array ModelerAppFileSummary; 404 |
| POST `/modeler-apps/{key}/files` | upsertModelerAppFile | multipart `file` (binary); upsert по деривированному file_key | 201 (создан) / 200 (заменен) ModelerAppFile; 400 (тип/парсинг/деривация); 404; 409 |
| GET `/modeler-apps/{key}/files/{fileKey}` | getModelerAppFile | Key, fileKey | 200 ModelerAppFile (метаданные: state, hashes, errors, engineResourceId); 404 |
| PUT `/modeler-apps/{key}/files/{fileKey}` | updateModelerAppFileContent | multipart `file`; деривированный key должен совпасть с {fileKey} | 200 ModelerAppFile; 400 (mismatch/парсинг); 404 |
| GET `/modeler-apps/{key}/files/{fileKey}/content` | getModelerAppFileContent | Key, fileKey | 200 сырые байты (application/xml для bpmn/dmn, application/json для bform, `Content-Disposition` с resourceName); 404 |
| DELETE `/modeler-apps/{key}/files/{fileKey}` | deleteModelerAppFile | Key, fileKey | 204 (если app deployed -> app становится ahead); 404 |
| (опционально) POST `/modeler-apps/{key}/undeploy` | undeployModelerApp | Key | 200 ModelerApp (снять деплой, вернуть в draft, не удаляя контейнер); 404; 409 |

Использовать существующие `#/components/responses/*` (BadRequest, Unauthorized,
Forbidden, NotFound, Conflict) и `parameters/Key`. `multipart` описывать как в
`deployApplication` (object с `file: {type: string, format: binary}`). Загрузка
одного файла - multipart `file`, чтобы переиспользовать конвенцию и переносить
filename (`resource_name`).

### 3.1 Новые схемы (PascalCase)

- `ModelerAppState` (enum: draft, synced, ahead), `ModelerFileType` (enum: bpmn,
  dmn, bform), `ModelerFileErrorSeverity` (enum: error, warning).
- `ModelerFileError` { severity: ModelerFileErrorSeverity, code: string,
  message: string, at: date-time }.
- `CreateModelerAppRequest` { key (required), name, description, readme }.
- `UpdateModelerAppRequest` { name, description, readme }.
- `ModelerAppFileSummary` { id(uuid), appKey, fileKey, name, type, state,
  resourceName, engineResourceId, errorCount }.
- `ModelerAppFile` (полные метаданные) { id, appKey, fileKey, name, description,
  type, state, resourceName, engineResourceId, contentHash, deployedHash,
  errors: [ModelerFileError], createdAt, updatedAt }.
- `ModelerAppRef` (компакт для списка) { id, key, name, description, state,
  appDefinitionId, deploymentId, deployedVersion, deployedAt, createdAt, updatedAt }.
- `ModelerApp` = allOf(ModelerAppRef, { readme, files: [ModelerAppFileSummary],
  deployedApplication: ApplicationRef (nullable) }). `deployedApplication`
  заполнять через переиспользование `ApplicationMapper`/`ApplicationService.getApplication`.
- `ModelerAppPage` с `x-kotlin-implements: ru.briany.common.api.dtos.PagedList<ModelerAppRef>`
  и `x-kotlin-implements-fields` (data,page,pageSize,totalElements,totalPages,
  hasNext,hasPrevious), allOf с `PagedResponse` - точно как `ApplicationPage`.

## 4. Деривация key/name/description (контент = источник истины)

`ModelerFileIntrospector` определяет тип по расширению (`.bpmn`/`.dmn`/`.bform`,
иначе 400) и извлекает идентичность строго:

- bpmn: распарсить XML; должно быть ровно одно `<process>` с непустым `id`.
  `file_key = process.id`, `name = process.name`, `description` - из
  `<documentation>` при наличии. Иначе - ошибка валидации (несколько/ноль process
  -> запись/деплой блокируются).
- dmn: ровно одно `<decision>` с `id` (или `<definitions>` id/name как fallback
  для name). `file_key = decision.id`, `name = decision.name`.
- bform: валидный JSON с непустым `id`. `file_key = id`,
  `name = components[0].label ?: id` (как в `FormService.deployForm`).

Колонки `file_key/name/description` - read-only проекция; отдельного PUT
метаданных файла нет. `resource_name` берется из имени загружаемого файла
(multipart filename); если пустое - синтезировать `"$fileKey.$type"`.

## 5. Правила состояний (пересчет по SHA-256)

Хэш файла: `content_hash = sha256hex(content)`.
Агрегатный хэш приложения:
`sha256hex( sortedByFileKey.joinToString("\n") { "$fileKey:$contentHash" } )`.

Состояние файла:

- app без `deployment_id` (никогда не деплоился) -> `draft`.
- app с `deployment_id`: `synced` если `content_hash == deployed_hash`, иначе
  `ahead` (в т.ч. файл, добавленный после деплоя, у которого `deployed_hash == null`).
- после успешного деплоя: `deployed_hash = content_hash`, state = `synced`.

Состояние приложения:

- нет `deployment_id` -> `draft`.
- есть `deployment_id`: `synced` если текущий агрегатный хэш ==
  `deployed_content_hash`, иначе `ahead`. (Агрегат покрывает
  добавление/удаление/изменение файлов одной колонкой.)

Пересчет вызывать после любой мутации файла/контейнера и после деплоя. `state`
хранится денормализованно (для фильтра в списках), но всегда пересчитывается из
хэшей - хэши являются истиной.

Переходы (сводно): создание app -> draft. Добавление/замена/удаление файла при
`synced` app -> app становится `ahead`. Успешный deploy -> app и все файлы
`synced`. Повторная заливка идентичного контента не меняет хэш -> остается `synced`.

## 6. Слой сервисов

### 6.1 ModelerAppService (`@Service`, `@Transactional(readOnly = true)`)

- `create(req)`: проверка уникальности key (иначе 409), state=draft.
- `list(stateFilter, pageable)`, `get(key)` (404), `update(key, req)` (метаданные
  name/description/readme, key неизменяем, bump updated_at).
- `delete(key)` (`@Transactional`): если `deployment_id != null` ->
  `deploymentService.delete(deployment_id)` (каскадный undeploy), затем удалить
  контейнер (файлы уходят по FK cascade).
- `upsertFile(key, resourceName, bytes)` (`@Transactional`): определить тип;
  `ModelerFileIntrospector` -> file_key/name/description + структурная валидация ->
  `error_log`; посчитать content_hash; upsert по (app_id, file_key); пересчитать
  state файла и агрегат/state app; bump updated_at.
- `updateFileContent(key, fileKey, bytes)`: как upsert, но деривированный key
  обязан совпасть с `fileKey` (иначе 400/409).
- `deleteFile(key, fileKey)`: удалить; пересчитать state app (если app deployed ->
  ahead).
- `getFile`, `listFiles`, `getFileContent` (возвращает bytes + media type по type).

### 6.2 ModelerAppDeployService (`@Service`)

- `deploy(key)` (`@Transactional`):
  1. загрузить app + файлы; guard: файлов >= 1 и ни у одного файла нет записей
     `severity=error` (иначе 400/409 с агрегированным Problem).
  2. синтезировать `.app` ресурс: JSON `{"key": app.key, "name": app.name}` ->
     запись в `flwFiles` под именем `"${app.key}.app"`.
  3. разложить файлы: `.bform` -> `bformFiles`, `.bpmn/.dmn` -> `flwFiles` (ключ -
     `resource_name`, значение - `content`).
  4. вызвать `deploymentService.deploy(deploymentName = app.key, flwFiles,
     bformFiles)` -> `Application`.
  5. линковка ресурсов: получить `resolveDeployedResources(appDeploymentId)` (см.
     7) и по `resource_name` проставить `engine_resource_id` каждому файлу.
  6. проставить app-ссылку: `app_definition_id`, `app_definition_key`,
     `deployment_id`, `deployed_version` из `Application`; каждому файлу
     `deployed_hash = content_hash`, state=synced; `deployed_content_hash` =
     агрегат; `deployed_at = now`; state app = synced; очистить `error` из
     `error_log` (warnings можно сохранить).
  7. вернуть `ModelerApp` c `deployedApplication`.
  - Ошибка деплоя движка: НЕ обновлять ссылку/состояния (откат транзакции),
    вернуть `ResponseStatusException(409)` с сообщениями валидации движка. Персист
    лога ошибок деплоя - опционально через отдельную транзакцию `REQUIRES_NEW`
    (иначе откат сотрет записи).
- `undeploy(key)` (опционально): `deploymentService.delete(deployment_id)`;
  обнулить ссылку и `deployed_*`, `engine_resource_id`, `deployed_hash`; state ->
  draft.

## 7. Точки интеграции с существующим кодом

- Переиспользовать без изменений: `DeploymentService.deploy(deploymentName,
  flwFiles, bformFiles)` (синтез `.app` делает `ModelerAppDeployService`),
  `DeploymentService.delete(deploymentId)`, `ApplicationService.getApplication(key)`,
  `ApplicationMapper.from(...)`/`Application`/`ApplicationRef`.
- Дополнить: линковка ресурсов после деплоя. Добавить метод (в `DeploymentService`
  или отдельный резолвер) `resolveDeployedResources(appDeploymentId): Map<String,
  ResourceRef>` (ключ = resource_name):
  - forms: `formService.getFormsByDeployment(appDeploymentId)` (в текущем
    `deploy()` формы деплоятся с `deploymentId = appDeployment.id`).
  - процессы/dmn: найти дочерние engine-деплойменты через
    `repositoryService.createDeploymentQuery().parentDeploymentId(appDeploymentId)`
    и `dmnRepositoryService...parentDeploymentId(...)`, затем перечислить
    определения по их `deploymentId`, ключевать по `resourceName`. (Тот же обход,
    что уже используется в `DeploymentService.delete`/`deleteRelatedDeployments`.)
  - Побочная польза: этим же методом заполнить `Application.deployedResources` в
    `ApplicationMapper` (сейчас не заполняется).
- Валидация BPMN на деплое уже обеспечивается
  `engine/config/validator/SafeBpmnDeploymentValidator` (+ activity whitelist,
  delegate allowlist) - ошибки движка ловить и агрегировать в Problem/лог.

## 8. Валидация и обработка ошибок

- Расширение файла в {.bpmn,.dmn,.bform}, иначе 400.
- Парсинг: валидный XML/JSON; строго один process/decision (см. 4); непустой id;
  иначе запись в `error_log` (severity=error) и HTTP 400 на самой операции записи.
- Уникальность file_key в пределах app обеспечивается upsert-логикой (замена при
  совпадении) и БД-констрейнтом.
- Deploy-guard: 0 файлов -> 400; наличие error-записей -> 409 с перечислением.
- Ошибки движка при деплое -> 409, детали в Problem (переиспользовать
  `GlobalExceptionHandler` + `ResponseStatusException`).
- `error_log` перезаписывается при каждой валидации файла; на успешном деплое
  удаляются error-записи.

## 9. Конвенции и качество (обязательно)

- detekt с первой итерации: без `catch(e: Exception)`, без `!!`, без `println`
  (SLF4J), без magic numbers (константы: длины полей, размер хэша, media types,
  суффиксы `.bpmn/.dmn/.bform/.app`), функции <= 60 строк, длина строки <= 140.
- `Instant` для времен; sealed-класс для `state`-фильтра списка по образцу
  `VersionFilter`.
- Контроллер имплементит сгенерированный `ModelerAppApi` (API-first): сначала
  правки `openapi-v1.yaml`, затем `openApiGenerate`, затем реализация.
- Без типографских символов в коде/markdown (прямые кавычки, дефис, `->`);
  любой текст ТОЛЬКО на английском.
- Без разделов "для чайников".

## 10. Тесты

- Unit: деривация key/name (bpmn/dmn/bform, случаи 0/1/несколько process ->
  ошибка), расчет content/aggregate hash, матрица переходов draft/synced/ahead
  (добавление, изменение, удаление, повтор идентичного контента, деплой).
- Integration (Testcontainers, по образцу `ApplicationControllerIT`/
  `DeploymentServiceIT`): create app -> add files -> deploy -> проверить
  `app_definition_id/deployment_id/deployed_version`, `engine_resource_id` файлов,
  state=synced; мутировать файл -> ahead; redeploy -> synced; delete -> каскадный
  undeploy (проверить отсутствие app/process/dmn/form деплойментов). Тестовые
  ресурсы - в духе `src/test/resources/deployments/*` (bpmn/dmn/bform).

## 11. Чек-лист поставки

1. `openapi-v1.yaml`: тег `ModelerApp`, 12 (+1 опциональная) операций, схемы из
   3.1.
2. Liquibase `0002_create_brn_modeler_app.sql` + запись в master changelog.
3. Entity/Repository/Service (2 шт.)/DeployService/Mapper/Controller/Converter/
   Introspector в `ru.briany.domain.modeler`.
4. Метод `resolveDeployedResources` + заполнение `Application.deployedResources`.
5. Unit + integration тесты.
6. detekt/ktlint чистые.
