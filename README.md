# briany-flowable-engine

Демонстрационный BPM-движок на встроенном Flowable 8: Kotlin, Spring Boot 4,
PostgreSQL. Показывает три вещи - контурную архитектуру вокруг встроенного движка,
low-code поверх headless Flowable 8 (рантайм-эндпоинты палитры и возможностей движка,
декларативные формы с FEEL, workspace-моделлер) и безопасность встроенного движка как
plugin-системы (JUEL-песочница, безусловный запрет script/shell-задач, whitelist
активностей, валидация деплоя, allowlist классов и делегатов). Сопроводительный
материал к серии статей в `article/`.

API-first: контракт в `contract/rest/openapi-v1.yaml` (git-сабмодуль), контроллеры
реализуют сгенерированные `*Api`-интерфейсы.

## Как поднять

Нужен Docker и собранный jar (`build/libs/*.jar`), который копируется в образ.

```sh
cp .env.example .env          # задать хотя бы IDM_ADMIN_PASSWORD
./gradlew bootJar             # собрать jar
docker compose up --build     # postgres + приложение, стратегия auth = flowable
```

- REST API: `http://localhost:8095`
- Actuator (health, prometheus): `http://localhost:8096/actuator/health`
- Логин по умолчанию: `admin` / значение `IDM_ADMIN_PASSWORD` (HTTP Basic).

### Вариант с JWKS (Keycloak)

Стратегия `jwks` вместо `flowable` - добавляет Keycloak с преднастроенным realm
`bpm` (`keycloak/bpm-realm.json`, пользователи `admin/admin` и `demo-user/demo-pass`):

```sh
docker compose -f docker-compose.yml -f docker-compose.jwks.yml up --build
```

Keycloak поднимется на `http://localhost:8080`. Приложение валидирует Bearer-токены
против realm `bpm`.

## Локальная разработка без Docker

Профиль `dev` берёт БД на `localhost:5452` и не требует аутентификации на engine API:

```sh
DB_PORT=5452 docker compose up db   # или свой postgres на 5452
./gradlew bootRun --args='--spring.profiles.active=dev'
```
