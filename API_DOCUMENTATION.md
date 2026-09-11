# HRGenius — API Documentation

Base URL: `http://localhost:8080/api/v1` · Live docs: `/swagger-ui.html` (springdoc)

All responses use the envelope `{ "success": boolean, "message": string, "data": <payload> }`.
All errors use `{ "timestamp", "status", "message", "path", "errors": { field: message } }`.

## Phase 0 (current)

### Health

`GET /api/v1/health` — public. Liveness + DB connectivity probe.

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "application": "HRGenius API",
    "status": "UP",
    "database": "UP",
    "timestamp": "2026-09-11T10:15:30.123"
  }
}
```

`database` is `UP` only when `SELECT 1` succeeds; the endpoint reports `DOWN` with HTTP 503 otherwise.

## Phase 1 — Authentication ✅

All paths below are relative to `/api/v1/auth`.

### POST /auth/login — public

Request:

```json
{ "email": "admin@hrgenius.local", "password": "Admin@123" }
```

Success `200`:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "tokenType": "Bearer",
    "expiresInMinutes": 60,
    "userId": 1,
    "email": "admin@hrgenius.local",
    "fullName": "System Administrator",
    "role": "ADMIN"
  }
}
```

Errors: `400` (validation, `errors` map populated) · `401` invalid email or password.
Send the token on every protected call as `Authorization: Bearer <token>`.

### GET /auth/me — JWT required

Returns the current user (no token field):

```json
{
  "success": true,
  "message": "OK",
  "data": { "userId": 1, "email": "admin@hrgenius.local", "fullName": "System Administrator", "role": "ADMIN" }
}
```

Errors: `401` missing/invalid/expired token (checked before route existence — unknown paths also 401).

### POST /auth/logout — JWT required

Bumps the user's server-side TOKEN_VERSION, instantly invalidating every token ever issued
to that user (stateless logout without a blacklist). Returns `200` with
`{ "success": true, "message": "Logged out" }`. Reusing a logged-out token yields `401`.

### RBAC smoke endpoint

`GET /api/v1/admin/only` — `@PreAuthorize("hasRole('ADMIN')")`. `200` for ADMIN, `403` for
any other authenticated role. Demonstrates the authorization chain used by all future modules.

## Phase 3+ — Employees (planned)

| Method | Path | Roles |
|---|---|---|
| GET | /employees | ADMIN, HR, MANAGER |
| GET | /employees/{id} | ADMIN, HR, MANAGER, self |
| POST | /employees | ADMIN, HR |
| PUT | /employees/{id} | ADMIN, HR |
| DELETE | /employees/{id} | ADMIN |

Standard query params on list endpoints: `page` (0-based), `size`, `sort` (e.g. `createdAt,desc`), plus module-specific filters.

## JWT & Security Notes

- Algorithm HS384; secret from `JWT_SECRET` env var (local dev default documented in `application.yml`).
- Claims: `sub` (email), `uid`, `role`, `ver` (token version), `iat`, `exp` (60 min).
- Every authenticated request re-loads the user and checks `ENABLED` + `TOKEN_VERSION`, so
  deactivation and logout take effect immediately without waiting for expiry.
- Passwords: BCrypt (strength 10), never returned by any API, never logged.
- Frontend stores the token in `localStorage`; 401 responses auto-clear the session and
  redirect to `/login` (except on the login call itself).

## Status Code Contract

| Code | Meaning |
|---|---|
| 200 | successful GET/PUT |
| 201 | resource created (Location header when applicable) |
| 204 | successful DELETE |
| 400 | validation error (see `errors` map) |
| 401 | missing/invalid/expired token |
| 403 | authenticated but role not permitted |
| 404 | resource not found |
| 409 | business conflict (duplicate, overlapping leave, etc.) |
| 500 | unexpected server error (message only, no stack trace) |
