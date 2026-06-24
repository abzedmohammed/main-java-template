# main-java-template

Authentication-first Spring Boot template — a balanced starting point for new services.

## What is included

- Local authentication (register / login) with BCrypt
- JWT access + refresh tokens via HttpOnly cookies, with refresh-token rotation
- Email verification + forgot / reset / update password flows (SMTP)
- Login throttling with temporary lockout (persisted)
- Optional social auth (OAuth2 login success handler)
- Role-based access (`USER` / `ADMIN`) with method security
- Optional admin bootstrap on startup
- Filterable system audit log (`ADMIN`-only endpoint)
- Optional RabbitMQ publishing of auth events
- Standard response envelope + typed error handling
- Flyway database migrations
- OpenAPI + Swagger UI
- Docker + docker-compose for local dependencies, GitHub + GitLab CI

## Stack

- Spring Boot 4.0.x, Spring Framework 7, Spring Security 7, Hibernate 7 — Java 21
- PostgreSQL (runtime) / H2 (tests)
- JWT via `jjwt`; OpenAPI via `springdoc-openapi`

## Requirements

- Java 21
- Maven (use the bundled `./mvnw`)
- PostgreSQL 17+ (or run it via docker-compose)
- Python 3 (only to scaffold a new project — see below)

## Create a new project from this template

Instead of copy-pasting, run the scaffolder. It produces a clean, fully renamed
local copy (Maven coordinates, base package + directory layout, main class, app/db
names, RabbitMQ queue names, titles), seeds a `.env` with fresh JWT secrets, and
`git init`s the result.

```bash
python scaffold.py ../acme-portal --package com.acme.portal --name "Acme Portal"
```

Options: `--package`, `--group`, `--artifact`, `--name`, `--no-git`, `--force`.
The artifact/main-class/db names are derived from the target directory unless
overridden. Run it from this repo — it copies the template it lives in.

## Generate a CRUD module

`generate_entity.py` scaffolds a full module (entity, repository, service,
controller, DTOs, mapper, Flyway migration) into the project's base package.

```bash
python generate_entity.py product name:string sku:code price:bigdecimal active:boolean --prefix prd
```

Conventions:

- **UUIDv7 primary keys** (`@UuidGenerator(algorithm = UuidVersion7Strategy.class)`) — time-ordered.
- **Prefixed fields** (`prdId`, `prdName`, `prdCreatedAt`, `prdUpdatedAt`).
- **3 endpoints only** — no GET/PUT/PATCH, all wrapped in the standard `ApiResponse` envelope:
  - `POST /api/products/save` — create when no id, update when id present
  - `POST /api/products/list` — `{ start, limit, search }`; list in `data.result`, count in `total`
  - `DELETE /api/products/{id}`
- A search query is generated across string-like fields; `--soft-delete` adds
  `prdDeleted`/`prdDeletedAt` and soft-delete logic.

Options: `--prefix`, `--soft-delete`, `--base-package`, `--force`. Field types:
`string, text, email, phone, code, url, slug, int, long, boolean, bigdecimal,
double, float, date, datetime, uuid`.

## Quick start

```bash
# 1. Start dependencies (Postgres + Mailpit)
docker compose up -d postgres mailpit

# 2. Configure secrets
cp .env.example .env   # then set APP_JWT_* secrets and export the vars

# 3. Run
./mvnw spring-boot:run
```

Or run everything (app included) in containers:

```bash
docker compose --profile full up --build
```

## Configuration

All settings come from environment variables — see `.env.example`. Key ones:

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `APP_JWT_ACCESS_SECRET` / `APP_JWT_REFRESH_SECRET` — base64, ≥32 bytes of key material
- `APP_CORS_ALLOWED_ORIGINS` — comma-separated origin patterns
- `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` — optional admin bootstrap (promotes the user to `ADMIN`, or creates the account when a password is supplied)
- `APP_SOCIAL_AUTH_ENABLED` + `spring.security.oauth2.client.*` for social login
- `APP_RABBITMQ_ENABLED` + `SPRING_RABBITMQ_*` for messaging (optional)

## Database migrations

Schema is owned by Flyway (`src/main/resources/db/migration`). `ddl-auto` is set to
`validate`, so the app refuses to start if the entities drift from the schema. Add a new
`V2__*.sql` for every change. Tests run on H2 with Flyway disabled.

## API

The full contract (envelope, error model, every endpoint) lives in
[`docs/project-apis.md`](docs/project-apis.md). Summary:

- `POST /api/auth/register`, `/verify-email`, `/login`, `/refresh`, `/logout`
- `POST /api/auth/forgot-password`, `/reset-password`
- `POST /api/auth/update-password` *(authenticated)*
- `GET  /api/auth/me` *(authenticated)*
- `GET  /api/system-logs` *(ADMIN)*
- Docs: `GET /api/docs`, `GET /swagger-ui`

Every response uses the standard envelope:

```json
{ "success": true, "message": "...", "data": { "result": null }, "total": 0, "targetUrl": null, "status": 200 }
```

## Testing

```bash
./mvnw verify
```

Runs unit tests (JWT, login throttling) and integration tests (full auth flow, token
rotation, access control) against H2.

## Local email testing

Mailpit ships in docker-compose — SMTP on `localhost:1025`, web UI on `http://localhost:8025`.

## RabbitMQ (optional)

Set `APP_RABBITMQ_ENABLED=true` and configure `SPRING_RABBITMQ_*`. Auth events (category
`AUTH`) are published to `main-template.auth.events`.
