# main-java-template

<!-- template-only:start -->
Authentication-first Spring Boot template: a balanced starting point for new services, and the
backend counterpart of the `abzed-app` React template (same response envelope, same auth contract).
<!-- template-only:end -->
<!-- project-only
Main Java Template: an authentication-first Spring Boot service. It speaks the same response
envelope and auth contract as the `abzed-app` React frontend.
-->

## What is included

- OTP-based local auth (sign up / sign in / forgot password) with BCrypt, matching the abzed-app frontend contract
- 4-digit email OTPs: HMAC-hashed at rest, single-use, attempt-capped; `otpTimer` returned for the client countdown
- Stateless JWT access token returned in the body (`Authorization: Bearer`), refreshed via `/auth/refresh-session`.
  No cookies, no server-side refresh tokens
- Account lifecycle (`DRAFT` → `ACTIVE`, `INACTIVE`, `DEACTIVATED`)
- Login throttling with temporary lockout (persisted)
- DB-driven, editable email templates: HTML layouts + per-channel message content, seeded on startup
- Optional social auth (OAuth2 login success handler)
- Role-based access (`USER` / `ADMIN`) with method security
- Optional admin bootstrap on startup
- Filterable system audit log (`ADMIN`-only endpoint)
- Optional RabbitMQ publishing of auth events
- Standard response envelope + typed error handling
- UUIDv7 primary keys and 3-letter prefixed fields (`usrId`, `otpPurpose`, ...)
- Flyway database migrations
- OpenAPI + Swagger UI
- Hot reload dev scripts (spring-boot-devtools) + a Claude Code hook
<!-- template-only:start -->
- Project scaffolder (`scaffold.py`)
<!-- template-only:end -->
- CRUD module generator (`generate_entity.py`)
- Docker + docker-compose for local dependencies, GitHub + GitLab CI

## Stack

- Java 25 (LTS)
- Spring Boot 4.1.2: Spring Framework 7, Spring Security 7, Hibernate 7
- PostgreSQL (runtime) / H2 in PostgreSQL mode (tests)
- Flyway, Lombok, JJWT, springdoc-openapi
- Maven 3.10 via the bundled wrapper (`./mvnw`, or `mvnw.cmd` on Windows)

## Requirements

- **JDK 25.** `./mvnw` builds with whatever `JAVA_HOME` points to, so make sure it is a JDK 25
  (`./mvnw -v` shows it). With JDK 21 the build fails with `release version 25 not supported`.
- PostgreSQL 17+ (docker-compose runs 18)
- Python 3, only for the Python helper scripts
- Bash for `scripts/` (Git Bash on Windows)

<!-- template-only:start -->
## Create a new project from this template

Instead of copy-pasting, run the scaffolder. It produces a clean, fully renamed local copy
(Maven coordinates, base package + directory layout, main class, app/db names, RabbitMQ queue
names, titles, scripts), seeds a `.env` with fresh JWT + OTP secrets, and `git init`s the result.

```bash
python scaffold.py "Project X"
```

Everything is derived from the name (run with no name to be prompted):

| | From `"Project X"` |
|---|---|
| Folder | `../project-x` (next to this template) |
| Package / groupId | `com.abzed.projectx` |
| Artifact | `project-x` (database `project_x`) |
| Display name | `Project X` |
| Main class | `ProjectXApplication` |

Optional overrides: `--package com.acme.portal`, `--dir <path>`, `--artifact`, `--group`,
`--no-git`, `--force`. The older form `python scaffold.py ../acme-portal --package com.acme.portal`
still works. The default package prefix is `DEFAULT_ORG` at the top of `scaffold.py`. Run it from
this repo, since it copies the template it lives in.

<!-- template-only:end -->

## Quick start

```bash
# 1. Start dependencies (Postgres + Mailpit)
docker compose up -d postgres mailpit

# 2. Configure secrets (a scaffolded project already has a .env with generated secrets)
cp .env.example .env   # then set APP_JWT_ACCESS_SECRET + APP_OTP_SECRET + DB_PASSWORD

# 3. Run (loads .env for you, with hot restart)
./scripts/dev-run.sh
```

Or run everything (app included) in containers:

```bash
docker compose --profile full up --build
```

Postgres 18 keeps its data under `/var/lib/postgresql`, which is where the compose volume is
mounted. A volume created by an older Postgres image won't open; reset it with
`docker compose down -v`.

### Dev scripts (hot reload)

`spring-boot-devtools` is on the runtime classpath (never in the packaged jar), so any recompile
hot-restarts the running app.

| Script | What it does |
|---|---|
| `scripts/dev-run.sh` | Loads `.env` (Spring doesn't read it natively) and runs `./mvnw spring-boot:run`. |
| `scripts/dev-watch.sh` | Run in a second terminal: polls `src/main` every 2s and recompiles on change. |
| `scripts/dev-reload.sh` | One-off offline incremental compile, which triggers a hot restart. |
| `scripts/dev-status.sh` | `UP`/`DOWN` for `APP_PORT` (default 8080) and the owning PID (Windows `netstat`). |
| `scripts/dev-reload-hook.sh` | Claude Code `PostToolUse` hook (wired in `.claude/settings.json`): after an edit under `src/main`, recompiles if a server is up, and reports compile errors back to the agent. |
| `scripts/setup-postgres.sh` | Creates the local database if it is missing. |

`dev-reload.sh` and the hook compile offline (`-o`), so run the app (or any `./mvnw` build) once
online first to download dependencies.

## Project layout

Package-by-feature under the base package `com.abzed.template`:

| Package | Contents |
|---|---|
| `auth` | `AuthController`/`AuthService`, OTP (`OtpService`, `OtpHasher`, `OtpToken`), JWT, password-reset authorization, OAuth2 success handler, request/response records |
| `auth.ratelimit` | Persisted login lockout |
| `user` | `User` entity, `UserStatus`/`UserRole`/`AuthProvider`, `UserPrincipal`, `UserResponse` |
| `template` | `MessagingService`, `EmailService`, email layouts + message templates, `TemplateSeeder` |
| `messaging` | Optional RabbitMQ auth-event publisher/listener |
| `common` | `ApiResponse` envelope, `GlobalExceptionHandler`, typed exceptions, `MaskingUtil`, system logs |
| `config` | Security, CORS, typed properties (`AuthProperties`), admin seeder, OpenAPI, JPA auditing |
| `audit` | `BaseEntity` (`createdAt` / `updatedAt`) |

## Generate a CRUD module

`generate_entity.py` scaffolds a full module (entity, repository, service, controller, DTOs,
mapper, Flyway migration) into the project's base package.

```bash
python generate_entity.py product name:string sku:code price:bigdecimal active:boolean --prefix prd
```

Conventions:

- **UUIDv7 primary keys** (`@UuidGenerator(algorithm = UuidVersion7Strategy.class)`), time-ordered.
- **Prefixed fields** (`prdId`, `prdName`, `prdCreatedAt`, `prdUpdatedAt`).
- **3 endpoints, POST for everything except delete**, all wrapped in the standard `ApiResponse` envelope:
  - `POST /api/products/save`: create when no id, update when id present
  - `POST /api/products/list`: `{ start, limit, search }`; list in `data.result`, count in `total`
  - `DELETE /api/products/{id}`
- A search query is generated across string-like fields; `--soft-delete` adds
  `prdDeleted`/`prdDeletedAt` and soft-delete logic.

Options: `--prefix`, `--soft-delete`, `--base-package`, `--force`. Field types:
`string, text, email, phone, code, url, slug, int, long, boolean, bigdecimal,
double, float, date, datetime, uuid`.

## Configuration

All settings come from environment variables; see `.env.example`. Key ones:

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `APP_JWT_ACCESS_SECRET`: base64, at least 32 bytes of key material. `APP_JWT_ACCESS_MINUTES` (default 15)
- `APP_OTP_SECRET`: HMAC key for OTP hashing, at least 32 chars (**required**, the app won't start without it)
- `APP_OTP_TTL_MINUTES` (default 5): OTP lifetime, also returned to the client as `otpTimer`. `APP_OTP_MAX_ATTEMPTS` (default 5)
- `APP_LOGIN_MAX_ATTEMPTS` / `APP_LOGIN_LOCK_MINUTES` (defaults 5 / 15): login lockout
- `APP_NAME` / `APP_SUPPORT_EMAIL` / `APP_FRONTEND_LOGIN_URL`: values injected into email templates
- `APP_CORS_ALLOWED_ORIGINS`: comma-separated origin patterns
- `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`: optional admin bootstrap (promotes the user to `ADMIN`, or creates the account when a password is supplied)
- `APP_SOCIAL_AUTH_ENABLED` + `spring.security.oauth2.client.*` for social login
- `APP_RABBITMQ_ENABLED` + `SPRING_RABBITMQ_*` for messaging (optional)

## Database migrations

Schema is owned by Flyway (`src/main/resources/db/migration`). `ddl-auto` is set to
`validate`, so the app refuses to start if the entities drift from the schema. `V1__init.sql`
holds the whole initial schema; add a new `V2__*.sql` for every later change. Column types
mirror Hibernate's PostgreSQL output. Tests run on H2 with Flyway disabled.

## API

The full contract (envelope, error model, field prefixes, every endpoint) lives in
[`docs/project-apis.md`](docs/project-apis.md). Summary:

```
register ─▶ /auth/sign_up        ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ session
login    ─▶ /auth/sign_in        ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ session
forgot   ─▶ /auth/reset_password ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ /auth/update_password
```

- `POST /auth/sign_up`, `/auth/sign_in`, `/auth/reset_password` → `{ reqId, maskedEmail, otpTimer }`
  (`otpTimer` is the OTP lifetime in minutes)
- `POST /auth/sign_in_otp` → `{ accessToken, user }` (or `null` for the reset flow), `/auth/resend_otp`
- `POST /auth/update_password` *(after a verified reset OTP)*
- `POST /auth/refresh-session` *(authenticated)* → `{ token }`
- `GET  /api/system-logs` *(ADMIN)*
- Docs: `GET /api/docs`, `GET /swagger-ui`

Every response uses the standard envelope:

```json
{ "success": true, "message": "...", "data": { "result": null }, "total": 0, "targetUrl": null, "status": 200 }
```

## Email templates

Outbound email is DB-driven so copy and design can change without a redeploy:

- `email_templates`: HTML layouts (`OTP`, `GENERAL`), sourced from `src/main/resources/templates/email/`
- `message_templates`: content per message (`OTP_LOGIN`, `OTP_REGISTRATION`, `OTP_PASSWORD_RESET`, `WELCOME`)

`TemplateSeeder` inserts any missing defaults on startup and never overwrites existing rows, so
edit the rows in the database to change live emails. `{{placeholders}}` such as `{{appName}}`,
`{{otpCode}}`, `{{expiryText}}` and `{{supportEmail}}` are filled at send time.

Mailpit ships in docker-compose for local testing: SMTP on `localhost:1025`, web UI on
`http://localhost:8025`.

## Testing

```bash
./mvnw verify
```

Runs unit tests (JWT, OTP hashing/verification, login throttling, template rendering) and
integration tests (sign-up/sign-in/reset OTP flows, `otpTimer`, session refresh, access control,
the seeded welcome email) against H2. Only SMTP is mocked: the real templates render, and the
tests read each OTP from what would have been emailed. Mockito runs as a `-javaagent` (configured
in the pom) because newer JDKs block agents that attach themselves at runtime.

## RabbitMQ (optional)

Set `APP_RABBITMQ_ENABLED=true` and configure `SPRING_RABBITMQ_*`. Auth events (category
`AUTH`) are published to `main-template.auth.events`.

## Troubleshooting

- **`release version 25 not supported`**: `JAVA_HOME` points to an older JDK. Point it at a JDK 25.
- **`./mvnw` in Git Bash fails installing Maven** (`fail to move MAVEN_HOME`, `Device or resource busy`):
  antivirus is locking the freshly extracted files. Run `mvnw.cmd -v` once (it installs fine), then
  copy `~/.m2/wrapper/dists/apache-maven-<version>/<folder mvnw.cmd created>` to the folder name the
  bash error message shows.
- **`dev-reload.sh` says an artifact "has not been downloaded"**: it compiles offline; run any online
  `./mvnw` build once.
- **App won't start, Hibernate reports a missing table**: Flyway did not run. Check that
  `flyway_schema_history` exists in the database, and look for Flyway lines in the startup log.
