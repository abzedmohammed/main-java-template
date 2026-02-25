# main-java-template

Authentication-first Spring Boot template extracted from `tetish_inn_backend` and cleaned for reuse.

## What is included

- Local authentication (register/login)
- JWT access + refresh token flow
- Token auth via HttpOnly cookies
- Optional session auth mode
- Optional social auth wiring (OAuth2 login)
- Forgot password + reset password + update password
- Email notifications for password reset flow
- User + refresh token persistence
- System log entity with human-readable log messages
- Global API response and exception handling

## Dependency strategy

- Uses latest stable Spring Boot parent configured in `pom.xml`.
- Security/web/data/mail/oauth dependencies are managed by Spring Boot BOM for compatibility.
- JWT uses `jjwt` (`0.13.0`, latest stable family for Java 21 support).

## Auth Modes

Set `APP_AUTH_MODE`:

- `token` (default): stateless JWT + cookie flow
- `session`: Spring Security session-based auth enabled

## Social Login (Optional)

Set `APP_SOCIAL_AUTH_ENABLED=true` and configure provider credentials under:

- `spring.security.oauth2.client.registration.*`
- `spring.security.oauth2.client.provider.*`

## API Endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/update-password`
- `GET /api/auth/me`
- `GET /api/system-logs`

## Setup

1. Copy `.env.example` values into your environment.
2. Ensure PostgreSQL is running.
3. For local email testing, run Mailpit/Mailhog and keep default SMTP env values.
4. Run:

```bash
./mvnw spring-boot:run
```

## Note on Generator

This template includes `generate_entity.py` as a minimal, cleaner entity generator for quick module bootstrap.
