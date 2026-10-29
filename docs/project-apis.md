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
| `BadRequestException` | 400 | Invalid/expired OTP, invalid `reqId` |
| `UnauthorizedException` / auth failures / `JwtException` | 401 | Invalid credentials, missing/expired token |
| `ForbiddenException` / `AccessDeniedException` | 403 | Deactivated account, non-admin hitting admin route |
| `NotFoundException` | 404 | Resource missing |
| `ConflictException` | 409 | Email / mobile number already in use |
| `TooManyRequestsException` | 429 | Login lockout, too many wrong OTP attempts |
| (any unhandled) | 500 | Generic message; details are logged, never returned |

## Authentication

OTP-based, multi-step, stateless JWT. Sign-up / sign-in / reset start a flow and return
`{ reqId, maskedEmail, otpTimer }`; the client verifies a 4-digit OTP (single-use,
attempt-capped, `APP_OTP_TTL_MINUTES` lifetime) via `sign_in_otp`. Login/registration complete
the session and return the access token in `data.result.accessToken`; the reset flow instead
authorizes a one-off `update_password`. The access token is sent on later requests as
`Authorization: Bearer <token>`. There are no cookies and no server-side refresh tokens.

```
register ─▶ /auth/sign_up        ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ session
login    ─▶ /auth/sign_in        ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ session
forgot   ─▶ /auth/reset_password ─▶ (reqId, maskedEmail, otpTimer) ─▶ OTP ─▶ /auth/sign_in_otp ─▶ /auth/update_password
```

Auth credential fields are intentionally un-prefixed (`email`, `secret`, `password`, `fullName`,
`phone`, `agreement`, `otp`); the flow id is `reqId` in every step (it is the `usrId` the flow
belongs to).

| Method | Path | Auth | Body | Notes |
|---|---|---|---|---|
| POST | `/auth/sign_up` | public | `{ fullName, phone, email, password, agreement }` | Creates a `DRAFT` user (or re-registers an existing `DRAFT`), emails a registration OTP. 409 "Email already in use" / "Mobile number already in use" when held by a non-`DRAFT` account. Verifying the OTP promotes `DRAFT` → `ACTIVE`. |
| POST | `/auth/sign_in` | public | `{ email, secret }` | Verifies credentials, emails a login OTP. 401 bad credentials, 401 "Please verify account before continuing" (`DRAFT`/`INACTIVE`), 403 deactivated, 429 locked. Status errors only appear after the password is verified. |
| POST | `/auth/sign_in_otp` | public | `{ reqId, otp }` | Login/registration → `{ accessToken, user }`. Reset → `data.result` is `null`. 400 invalid/expired, 429 too many wrong attempts. |
| POST | `/auth/resend_otp` | public | `{ reqId }` | Re-issues the active flow's OTP (new code). `data.result` is `null`. |
| POST | `/auth/reset_password` | public | `{ email }` | Emails a reset OTP. Unknown emails still succeed with a believable response (no enumeration). 403 deactivated, 401 not active. |
| POST | `/auth/update_password` | pre-auth (reset) | `{ reqId, password }` | Authorized by a reset OTP verified in the last 15 minutes. 401 if not authorized/expired. |
| POST | `/auth/refresh-session` | **authenticated** | — | Re-issues an access token: `{ token }`. |

Init response `data.result` (`sign_up`, `sign_in`, `reset_password`):

```json
{ "reqId": "0190a1b2-...", "maskedEmail": "ja****@example.com", "otpTimer": 5 }
```

`otpTimer` is the OTP lifetime in **minutes** (`APP_OTP_TTL_MINUTES`). The frontend multiplies it
by 60 for its resend countdown and falls back to `VITE_DEFAULT_TIMER` when absent.

Session response `data.result` (`sign_in_otp`, login/registration):

```json
{
  "accessToken": "eyJ...",
  "user": {
    "usrId": "0190a1b2-...",
    "usrEmail": "jane@example.com",
    "usrFullName": "Jane Doe",
    "usrMobileNumber": "0712345678",
    "usrRoleCode": "USER"
  }
}
```

## Field naming

All entity fields use a 3-letter, per-entity prefix. Shared audit fields (`createdAt`/`updatedAt`)
stay unprefixed. Auth-token responses (`accessToken`, `token`) and the auth credential fields above
are not entity data and keep plain names.

| Entity | Prefix | Fields |
|---|---|---|
| User | `usr` | `usrId`, `usrFullName`, `usrEmail`, `usrMobileNumber`, `usrPasswordHash` (never serialized), `usrRole`, `usrProvider`, `usrProviderId`, `usrEmailVerified`, `usrStatus` (DRAFT/ACTIVE/INACTIVE/DEACTIVATED) |
| OtpToken | `otp` | `otpId`, `otpUserId`, `otpCodeHash` (HMAC-SHA256 hex, never serialized), `otpPurpose` (LOGIN/REGISTRATION/PASSWORD_RESET), `otpExpiryDate`, `otpConsumed`, `otpAttempts` |
| PasswordResetToken | `prt` | `prtId`, `prtToken`, `prtUserId`, `prtExpiryDate`, `prtUsed` |
| LoginRateLimit | `lrl` | `lrlId`, `lrlEmail`, `lrlAttempts`, `lrlBlockedUntil`, `lrlUpdatedAt` |
| SystemLog | `slg` | `slgId`, `slgLevel`, `slgCategory`, `slgTitle`, `slgDetails`, `slgActor`, `slgStatus` |
| EmailTemplate | `eml` | `emlId`, `emlCode`, `emlName`, `emlHtml`, `emlActive` |
| MessageTemplate | `mtp` | `mtpId`, `mtpCode`, `mtpChannel` (EMAIL/SMS/IN_APP), `mtpSubject`, `mtpTitle`, `mtpBody`, `mtpLayoutCode`, `mtpActive` |

## Messaging & email templates

Outbound messages are DB-driven and reusable (no endpoints; internal to the backend), split across two tables:

- **`message_templates`** (`mtp`): reusable content per channel (`EMAIL` / `SMS` / `IN_APP`). `mtpSubject`, `mtpTitle` and `mtpBody` support `{{placeholder}}` interpolation; `mtpLayoutCode` picks the email design that `EMAIL` content is wrapped in. Seeded: `OTP_LOGIN`, `OTP_REGISTRATION`, `OTP_PASSWORD_RESET`, `WELCOME`.
- **`email_templates`** (`eml`): the HTML design shells with `{{placeholder}}` slots, `OTP` and `GENERAL` (source HTML in `src/main/resources/templates/email/`).

`TemplateSeeder` inserts the defaults on startup only when a code is missing, so rows can be edited in the DB at runtime without a redeploy. At send time `MessagingService` resolves the message template by code, interpolates it with runtime variables (e.g. `otpCode`, `expiryText`) plus defaults (`appName`, `year`, `supportEmail`, `actionUrl`/`ctaUrl` = `APP_FRONTEND_LOGIN_URL`), wraps it in the referenced layout, and sends HTML via `EmailService.sendHtml`. Unknown placeholders render as empty strings.

Layout slots: `OTP` → `{{appName}}`, `{{title}}`, `{{body}}`, `{{otpCode}}`, `{{expiryText}}`, `{{actionUrl}}`, `{{actionLabel}}`, `{{supportEmail}}`, `{{year}}`; `GENERAL` → `{{appName}}`, `{{title}}`, `{{body}}`, `{{ctaUrl}}`, `{{ctaLabel}}`, `{{featureTitle}}`, `{{featureBody}}`, `{{year}}`.

## System logs

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/system-logs` | **ADMIN** | Filterable audit log. Params: `level`, `category`, `from`, `to` (ISO-8601), `page`, `size`. Returns `data.result` (list of `{ slgId, createdAt, slgLevel, slgCategory, slgTitle, slgDetails, slgActor, slgStatus, humanMessage }`) + `total`. |

## Docs & health

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/` | public | Liveness message. |
| GET | `/api/docs` | public | OpenAPI JSON. |
| GET | `/swagger-ui` | public | Swagger UI. |
