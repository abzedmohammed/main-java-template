# main-java-template

Authentication-first Spring Boot template extracted from `tetish_inn_backend` and cleaned for reuse.

## What is included

- Local authentication (register/login)
- JWT access + refresh token flow
- Token auth via HttpOnly cookies
- Refresh-token rotation on refresh
- Login attempt throttling (temporary lockout)
- Optional session auth mode
- Optional social auth wiring (OAuth2 login success handler)
- Forgot password + reset password + update password
- Email verification flow
- Email notifications for verification/reset flows
- User + token persistence (refresh + reset + verification tokens)
- System log entity with human-readable log messages
- Global API response and exception handling

## Dependency strategy

- Uses latest stable Spring Boot parent configured in `pom.xml`.
- Security/web/data/mail/oauth dependencies are managed by Spring Boot BOM for compatibility.
- JWT uses `jjwt` (`0.13.0`, latest stable family for Java 21 support).

## Requirements (already available on your machine)

- Java 21
- Maven 3.8+
- PostgreSQL 17+

## Auth Modes

Set `APP_AUTH_MODE`:

- `token` (default): stateless JWT + cookie flow
- `session`: Spring Security session-based auth enabled

## Social Login (Optional)

Set `APP_SOCIAL_AUTH_ENABLED=true` and configure provider credentials under:

- `spring.security.oauth2.client.registration.*`
- `spring.security.oauth2.client.provider.*`

By default, OAuth success redirects to `APP_FRONTEND_OAUTH_SUCCESS_URL`.

## API Endpoints

- `POST /api/auth/register`
- `POST /api/auth/verify-email?token=...`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/update-password`
- `GET /api/auth/me`
- `GET /api/system-logs`

## PostgreSQL setup

1. Export env values from `.env.example`.
2. Ensure PostgreSQL is running.
3. Create DB:

```bash
cd ~/Documents/JAVA/main-java-template
./scripts/setup-postgres.sh
```

If your `postgres` user requires a different password, set env before running:

```bash
export DB_USER=postgres
export DB_PASSWORD=your_real_password
```

## Run

```bash
cd ~/Documents/JAVA/main-java-template
./mvnw spring-boot:run
```

## Local email testing

Use Mailpit/Mailhog with defaults from `.env.example`:

- SMTP host: `localhost`
- SMTP port: `1025`

## Note on Generator

This template includes `generate_entity.py` as a minimal, cleaner entity generator for quick module bootstrap.
