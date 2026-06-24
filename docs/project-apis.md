# Project APIs

Source-of-truth for this backend's API contract. Keep it in sync with the controllers.

## Response envelope

Every endpoint returns the standard envelope:

```json
{
  "success": true,
  "message": "Request successful",
  "data": { "result": null },
  "total": 0,
  "targetUrl": null,
  "status": 200
}
```

- Business payload lives under `data.result` (object, array, or null).
- List endpoints set the top-level `total`.
- `status` mirrors the HTTP status code.
- On error, `success` is `false`, `data` is `null`, and `message` carries the reason.

This matches the frontend contract: `usePaginatedQuery` reads `data.total` + `data.data.result`; `useDynamicMutation` reads `data.result` + `message`.

## Error model

Errors are mapped from typed exceptions to HTTP status codes by `GlobalExceptionHandler`:

| Exception | Status | Example |
|---|---|---|
| `BadRequestException` | 400 | Wrong current password, invalid/expired token |
| `UnauthorizedException` / auth failures / `JwtException` | 401 | Invalid credentials, missing/expired token |
| `ForbiddenException` / `AccessDeniedException` | 403 | Email not verified, non-admin hitting admin route |
| `NotFoundException` | 404 | Resource missing |
| `ConflictException` | 409 | Email already in use |
| `TooManyRequestsException` | 429 | Login lockout |
| (any unhandled) | 500 | Generic message; details are logged, never returned |

## Authentication

JWT access + refresh tokens delivered as HttpOnly cookies (`accessToken`, `refreshToken`). The access token is also returned in the `login`/`refresh` body for non-browser clients. Refresh tokens are rotated on every `/refresh` and revoked on logout, password update, and re-login.

| Method | Path | Auth | Body | Notes |
|---|---|---|---|---|
| POST | `/api/auth/register` | public | `{ fullName, email, password }` | Creates a USER, sends verification email. Returns the new user id in `data.result`. |
| POST | `/api/auth/verify-email?token=...` | public | — | Marks the email verified. |
| POST | `/api/auth/login` | public | `{ email, password }` | Sets cookies. 401 on bad credentials, 403 if unverified, 429 if locked. |
| POST | `/api/auth/refresh` | refresh cookie | — | Rotates tokens. 401 if the refresh token is invalid/expired/revoked. |
| POST | `/api/auth/logout` | refresh cookie | — | Revokes the refresh token and clears cookies. |
| POST | `/api/auth/forgot-password` | public | `{ email }` | Always returns success (no account enumeration). |
| POST | `/api/auth/reset-password` | public | `{ token, newPassword }` | 400 on invalid/expired token. |
| POST | `/api/auth/update-password` | **authenticated** | `{ currentPassword, newPassword }` | Revokes existing refresh tokens. |
| GET | `/api/auth/me` | **authenticated** | — | Returns `UserResponse` (never the password hash). |

## System logs

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/system-logs` | **ADMIN** | Filterable audit log. Params: `level`, `category`, `from`, `to` (ISO-8601), `page`, `size`. Returns `data.result` (list) + `total`. |

## Docs & health

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/` | public | Liveness message. |
| GET | `/api/docs` | public | OpenAPI JSON. |
| GET | `/swagger-ui` | public | Swagger UI. |
