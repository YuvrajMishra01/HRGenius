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

## Phase 1 — Authentication (planned)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /auth/login | public | email + password → `{ token, user }` |
| GET | /auth/me | JWT | current user profile |
| POST | /auth/logout | JWT | client-side token discard (stateless JWT) |

## Phase 3+ — Employees (planned)

| Method | Path | Roles |
|---|---|---|
| GET | /employees | ADMIN, HR, MANAGER |
| GET | /employees/{id} | ADMIN, HR, MANAGER, self |
| POST | /employees | ADMIN, HR |
| PUT | /employees/{id} | ADMIN, HR |
| DELETE | /employees/{id} | ADMIN |

Standard query params on list endpoints: `page` (0-based), `size`, `sort` (e.g. `createdAt,desc`), plus module-specific filters.

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
