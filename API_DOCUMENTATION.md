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

## Phase 2 — Dashboard ✅

### GET /dashboard/stats — JWT required, roles: ADMIN, HR

One round trip feeding the whole admin dashboard. All aggregation happens in the database
(GROUP BY / aggregate queries) — never in Java.

Response `data` shape:

```json
{
  "kpis": {
    "totalEmployees": 7, "activeEmployees": 7, "newHiresLast30Days": 1,
    "openPositions": 2, "totalCandidates": 4, "pendingLeaveRequests": 2
  },
  "departmentDistribution": [{ "name": "Engineering", "count": 4 }],
  "hiringTrend": [{ "year": 2026, "month": 9, "count": 1 }],
  "hiringPipeline": [{ "status": "APPLIED", "count": 1 }],
  "attendanceToday": {
    "day": "2026-09-11", "present": 0, "absent": 0, "halfDay": 0,
    "onLeave": 0, "holiday": 0, "totalRecords": 0, "attendancePercent": 0.0
  },
  "leaveSummary": [{ "status": "PENDING", "count": 2 }],
  "payrollThisMonth": { "year": 2026, "month": 9, "payslips": 0, "totalNet": 0 },
  "recentHires": [{ "id": 7, "employeeCode": "EMP007", "fullName": "Rohan Kulkarni",
                    "department": "Engineering", "designation": "Software Engineer",
                    "joiningDate": "2026-09-01", "status": "ACTIVE" }],
  "pendingApprovals": [{ "id": 2, "employeeName": "Vikram Singh",
                          "leaveType": "SICK_LEAVE", "startDate": "2026-09-13",
                          "endDate": "2026-09-14", "status": "PENDING" }],
  "upcomingInterviews": [{ "id": 1, "candidateName": "Kavya Rao",
                           "jobTitle": "Backend Developer",
                           "interviewDate": "2026-09-13T18:02:00+05:30", "mode": "ONLINE" }]
}
```

- `hiringPipeline` always contains all six stages in stable order (zero-filled).
- `hiringTrend` covers the trailing 12 months; empty months are absent.
- `recentHires` = last 30 days, max 5 · `pendingApprovals` = oldest first, max 5 ·
  `upcomingInterviews` = next 5 SCHEDULED from now.
- Errors: `401` unauthenticated · `403` MANAGER/EMPLOYEE (manager-scoped variant planned).

## Phase 3 — Employees ✅

Base path: `/api/v1/employees`. List endpoints support
`?page=0&size=10&sortBy=employeeCode&sortDir=asc` plus filters.

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | /employees | ADMIN, HR, MANAGER | paginated, sortable, filterable |
| GET | /employees/{id} | ADMIN, HR, MANAGER | 404 when unknown |
| POST | /employees | ADMIN, HR | 201 · 400 validation · 409 duplicates · 404 bad FK |
| PUT | /employees/{id} | ADMIN, HR | 200 · same error contract as POST |
| DELETE | /employees/{id} | ADMIN | **soft delete** → status TERMINATED |

### GET /employees — query parameters

| Param | Meaning |
|---|---|
| `search` | case-insensitive contains across name, code, email |
| `departmentId` | exact department |
| `status` | ACTIVE, ON_LEAVE, RESIGNED, TERMINATED |
| `employmentType` | FULL_TIME, PART_TIME, CONTRACT, INTERN |
| `page` / `size` | 0-based page, size capped at 100 |
| `sortBy` | whitelist: employeeCode, firstName, lastName, email, joiningDate, status, createdAt |
| `sortDir` | asc / desc (anything but "desc" = asc) |

Response `data`: `{ content, page, size, totalElements, totalPages, first, last }`.

### POST/PUT body

```json
{
  "employeeCode": "EMP010",
  "firstName": "Asha", "lastName": "Kaur",
  "email": "asha.kaur@hrgenius.local",
  "phone": "+91-…", "dateOfBirth": "1996-04-02", "gender": "FEMALE",
  "address": "Pune, India",
  "joiningDate": "2026-09-01",
  "employmentType": "FULL_TIME", "status": "ACTIVE",
  "departmentId": 1, "designationId": 1, "managerId": null
}
```

Validation errors return `400` with `errors: { field: message }`. Business conflicts
(duplicate code/email) return `409`. A designation from a different department is `400`
(`IllegalArgumentException`). Self-management (`managerId = id`) is `409`.

### DELETE semantics

Soft delete only: the row stays (history intact) with `status: TERMINATED`. Returns `409`
while the employee still manages others ("Reassign managed employees…").

### Supporting read endpoints

The Phase 3 dropdown endpoints graduated to full CRUD in Phase 4 — see the
Departments & Designations section below.

## Phase 4 — Departments & Designations ✅

Base paths: `/api/v1/departments` and `/api/v1/designations`. Reads: ADMIN, HR, MANAGER.
Writes (create/update/delete): **ADMIN only**.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /departments | 200 — with `managerName` + `employeeCount` | 401 |
| GET | /departments/{id} | 200 | 404 |
| POST | /departments | 201 | 400 validation · 409 duplicate name |
| PUT | /departments/{id} | 200 | 400 / 404 / 409 |
| DELETE | /departments/{id} | 204 | 404 · **409 while it still has employees** |
| GET | /designations?departmentId= | 200 — with `departmentName` + `employeeCount` | 401 |
| POST | /designations | 201 | 400 · 409 duplicate title per department · 404 bad FK |
| PUT | /designations/{id} | 200 | 400 / 404 / 409 |
| DELETE | /designations/{id} | 204 | 404 · **409 while employees hold it** |

Department body: `{ "name" (required, unique), "description", "managerId" (optional,
must reference an existing employee) }`. Responses carry `managerName` and
`employeeCount`, computed with grouped count queries (no N+1). Designation body:
`{ "title" (required), "departmentId", "description" }` — `title` is unique within a
department. Deleting a non-empty department or an in-use designation returns `409`.

### GET /employees/options

`GET /api/v1/employees/options?search=` — lightweight manager-picker projection:
`[{ "id": 3, "employeeCode": "EMP003", "fullName": "Vikram Singh",
"departmentName": "Engineering" }]` (TERMINATED excluded; optional search across name
and code). Used by the department dialog (manager) and the employee dialog (manager
picker, replacing the Phase 3 placeholder).

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
