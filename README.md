# Visit Pass (КР №3)

Spring Boot 3.5.5 backend системы заявок на посещение + статичный web UI. PostgreSQL 17 через Docker Compose, миграции Flyway, Swagger.

## Запуск

```sh
export JAVA_HOME=$(brew --prefix openjdk@21)
cp .env.example .env   # заполнить секреты, не коммитить
cd backend && ./start-compose.sh
```

Адреса:

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API docs: `http://localhost:8080/api-docs`

Остановка с сохранением данных: `docker compose -f backend/docker-compose.yml down` (volume `pgdata` остаётся).

## Тестовый админ (запланировано, Task 4 — auth)

Будет создаваться при старте из `.env` (`BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PASSWORD`), если таблица `users` пуста.

Вход (после Task 4): `POST /api/auth/login` с email и паролем тестового админа.

## Тесты

```sh
export JAVA_HOME=$(brew --prefix openjdk@21)
cd backend && mvn -q test
```
