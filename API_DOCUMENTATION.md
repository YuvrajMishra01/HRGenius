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

`GET /api/v1/employees/options?search=` — lightweight picker projection:
`[{ "id": 3, "label": "Vikram Singh (EMP003)" }]` (TERMINATED excluded; optional search
across name and code). Used by the department dialog (manager), the employee dialog
(manager picker), and interview scheduling (interviewer picker).

## Phase 5 — Recruitment ✅

Reads: ADMIN, HR, MANAGER · Writes (all four resources): **ADMIN, HR**.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /jobs | 200 — `[{ …job, applicationCount }]` | 401 |
| POST | /jobs | 201 | 400 validation · 404 unknown department |
| PUT | /jobs/{id} | 200 | 400 / 404 |
| DELETE | /jobs/{id} | 200 | 404 · **409 while applications exist** |
| GET | /candidates | 200 — with `applicationCount`, newest first | 401 |
| POST | /candidates | 201 | 400 validation · **409 duplicate email** |
| PUT | /candidates/{id} | 200 | 400 / 404 / 409 email owned by another candidate |
| DELETE | /candidates/{id} | 200 | 404 · **409 while applications exist** |
| GET | /applications | 200 — candidate/job/department resolved | 401 |
| POST | /applications | 201 | 400 job not OPEN · **409 duplicate (candidate, job)** |
| PATCH | /applications/{id}/status | 200 | 404 · **409 illegal stage move** |
| GET | /interviews | 200 — candidate/job/interviewer resolved | 401 |
| POST | /interviews | 201 | 404 · 400 past date · **409 wrong stage or live interview exists** |
| PATCH | /interviews/{id} | 200 — reschedule (SCHEDULED only) | 404 / 409 |
| POST | /interviews/{id}/complete | 200 — feedback + result | 404 / 409 |
| POST | /interviews/{id}/cancel | 200 | 404 / 409 |

### Pipeline transition map

`APPLIED → SCREENING → SHORTLISTED → INTERVIEW → SELECTED | REJECTED`, where REJECTED
is reachable from SCREENING, SHORTLISTED and INTERVIEW. Terminal stages (`SELECTED`,
`REJECTED`) cannot move; any other move is `409` with
`Cannot move application from <current> to <target>`. Candidate status is kept in sync
with pipeline progress.

### Interview rules

Scheduling is allowed only for `SHORTLISTED` or `INTERVIEW` applications and
**auto-advances SHORTLISTED → INTERVIEW**. Only one live (`SCHEDULED`) interview per
application. Completing records feedback + result (PASS/FAIL/ON_HOLD) but deliberately
does not move the pipeline — HR moves applications explicitly.

## Phase 6 — Onboarding ✅

Reads: ADMIN, HR, MANAGER · Writes: **ADMIN, HR**.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /onboardings | 200 — employee/application resolved + checklist | 401 |
| POST | /onboardings/start-application | 201 — converts SELECTED → employee | 404 · **409 not SELECTED / already onboarded** |
| POST | /onboardings/start-employee | 201 — record for an existing employee | 404 · **409 record exists** |
| PATCH | /onboardings/{id}/checklist | 200 — `{ itemIndex, done }` | 404 · 400 bad index |

### Conversion (POST /onboardings/start-application)

Requires a `SELECTED` application (one onboarding per application). Creates the
Employee: next free `EMP###` code, name split from the candidate, generated unique
`<local-part>@hrgenius.local` email, job's department, ACTIVE, FULL_TIME. Marks the
candidate **HIRED** and opens the default 8-item checklist at PENDING / 0%.
Joining date defaults to today.

### Checklist

Stored as a JSON array in the `CHECKLIST` CLOB (JPA converter). Toggling an item
recomputes `completionPercentage` (done/total × 100) and derives status:
0 done → `PENDING`, partial → `IN_PROGRESS`, all done → `COMPLETED`.
A null/empty checklist column reads as the default 8-item template, all unchecked.

## Phase 7 — Attendance ✅

Reads: ADMIN, HR, MANAGER · Writes (check-in/out, marking): **ADMIN, HR**
(user accounts are not yet linked to employees, so self-service is deferred).

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /attendance/today | 200 — `{ date, summary, records }` | 401 |
| GET | /attendance/month?year=&month= | 200 — per-employee roll-up + day map (defaults to current month) | 401 |
| POST | /attendance/check-in?employeeId= | 201 — PRESENT record with check-in | 404 · **409 already checked in** |
| POST | /attendance/check-out?employeeId= | 200 — sets check-out + `workingHours` | 404 · **409 no check-in / already checked out** |
| POST | /attendance/mark | 200 — upsert `{ employeeId, date, status }` | 404 · 400 validation |

### Rules

- One record per employee per day (`UK_ATT_EMP_DATE`).
- Check-in creates a `PRESENT` record; check-out computes `workingHours`
  (minutes/60, 2 dp; negative duration clamps to 0).
- `mark` upserts the day: working statuses (PRESENT/HALF_DAY) keep existing
  check-in/out times; ABSENT/LEAVE/HOLIDAY **clear** them (and hours).
- Month rows cover every employee (0-record rows included);
  `attendancePercent = (present + 0.5 × halfDay) / totalRecords × 100`, rounded to 1 dp.
- Statuses: `PRESENT, HALF_DAY, ABSENT, LEAVE, HOLIDAY` (CK_ATT_STATUS).

## Phase 8 — Leave ✅

Reads: ADMIN, HR, MANAGER · Writes (types, requests, decisions, deletes): **ADMIN, HR**.
All success payloads use the standard `{ success, message, data }` envelope; DELETE returns 204.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /leave/types | 200 — all types with `usageCount` | 401 |
| POST | /leave/types | 201 — created type | 400 validation · **409 duplicate name** |
| PATCH | /leave/types/{id} | 200 — updated type | 404 · **409 duplicate name** |
| DELETE | /leave/types/{id} | 204 | 404 · **409 type has requests** |
| GET | /leave/requests?status= | 200 — rich list (optional status filter, newest first) | 401 |
| POST | /leave/requests | 201 — PENDING request with `workingDays` | 404 · 400 range · **409 overlap / insufficient balance** |
| PATCH | /leave/requests/{id}/approve | 200 — APPROVED + `approverEmail` | 404 · **409 not PENDING · 409 balance re-check** |
| PATCH | /leave/requests/{id}/reject | 200 — REJECTED + `approverEmail` | 404 · **409 not PENDING** |
| PATCH | /leave/requests/{id}/cancel | 200 — PENDING → CANCELLED | 404 · **409 not PENDING** |
| DELETE | /leave/requests/{id} | 204 — decided requests only | 404 · **409 request is PENDING** |
| GET | /leave/balances/{employeeId}?year= | 200 — per-type `usedDays` / `remainingDays` | 404 |
| GET | /leave/summary | 200 — page KPIs (`approvalRate` 1 dp) | 401 |

### Rules

- Balance accounting counts **calendar days** of APPROVED requests whose start date falls
  inside the year; the API's `workingDays` (display) excludes SATURDAY/SUNDAY.
- Overlap guard: a new request may not intersect an existing PENDING/APPROVED request of the
  same employee (single query; CANCELLED/REJECTED don't block).
- Balance guard runs at **submission and again at approval**, so concurrent approvals cannot
  overdraw a type's `yearlyLimit`.
- Approval records the deciding user (`APPROVED_BY` audit column) from the JWT principal.
- Cancel keeps history (PENDING → CANCELLED); hard delete is allowed only for decided requests.

## Phase 9 — Payroll ✅

Reads: ADMIN, HR, MANAGER · Writes (run, components, lifecycle, delete): **ADMIN, HR**.
The EMPLOYEE role has no payroll access (sensitive financial data).
All success payloads use the standard `{ success, message, data }` envelope; DELETE returns 204.

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | /payrolls/run | 201 — `{ year, month, created, skipped, periodPayslips, periodTotalNet }` | 400 validation · **400 future period** |
| GET | /payrolls | 200 — all periods with totals, newest first | 401 |
| GET | /payrolls/{year}/{month} | 200 — `{ summary, payslips }` | 400 range |
| PATCH | /payrolls/{id}/components | 200 — updated row, net recomputed | 404 · 400 negative · **409 PAID immutable** |
| PATCH | /payrolls/{id}/process | 200 — DRAFT → PROCESSED | 404 · **409 not DRAFT** |
| PATCH | /payrolls/{id}/pay | 200 — PROCESSED → PAID | 404 · **409 not PROCESSED** |
| DELETE | /payrolls/{id} | 204 — DRAFT/PROCESSED only | 404 · **409 PAID permanent** |

### Rules

- A run creates one DRAFT payslip per ACTIVE employee not yet in the period; existing rows
  are skipped and never modified (idempotent re-runs).
- New drafts are pre-filled from the employee's latest payslip (or zeros) — but `netSalary`
  is always **recomputed** as basic + allowances − deductions − tax, clamped at 0, never
  copied from stored values.
- Lifecycle is one-way `DRAFT → PROCESSED → PAID`; PAID payslips are permanent financial
  records (no edit, no re-pay, no delete). Corrections after payment require a new run.
- Money is BigDecimal end-to-end (NUMBER(12,2)); no floating point anywhere in the math.
- Period validation: month 1–12, year 2000–2999, no future periods.

## Phase 10 — Performance ✅

Reads (reviews, summary): ADMIN, HR, MANAGER · Writes (create, edit, rate, acknowledge, delete): **ADMIN, HR**.
All success payloads use the standard `{ success, message, data }` envelope; DELETE returns 204.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /performance/reviews | 200 — list with employee/reviewer names | 401 |
| GET | /performance/summary | 200 — counts per status, average rating (official ratings only), distribution | 401 |
| POST | /performance/reviews | 201 — DRAFT review | 400 validation · 404 unknown employee/reviewer · **409 duplicate employee+period** |
| PATCH | /performance/reviews/{id} | 200 — edited DRAFT | 404 · **409 not DRAFT** |
| PATCH | /performance/reviews/{id}/rate | 200 — rating 1–5 + narrative, DRAFT → SUBMITTED | 404 · 400 rating out of range · **409 not DRAFT** |
| PATCH | /performance/reviews/{id}/acknowledge | 200 — SUBMITTED → ACKNOWLEDGED | 404 · **409 not SUBMITTED** |
| DELETE | /performance/reviews/{id} | 204 — DRAFT only | 404 · **409 not DRAFT** |

### Rules

- One review per employee per period — duplicates are rejected with 409 at creation.
- Lifecycle is one-way `DRAFT → SUBMITTED → ACKNOWLEDGED`; only DRAFT reviews can be edited
  or deleted. The employee of a review can never be assigned as its own reviewer.
- `rating` (1–5) may exist on a DRAFT as reviewer notes, but aggregates (average, distribution)
  count **SUBMITTED/ACKNOWLEDGED only** — a draft rating is never official.
- Acknowledge is the employee sign-off: no rating required, no comments, idempotent-free
  (second attempt 409).

## Phase 11 — Documents ✅

Reads (list, download): ADMIN, HR, MANAGER · Upload: **ADMIN, HR** · Delete: **ADMIN, HR**.
MANAGER is deliberately not allowed to upload. All list payloads use the standard envelope;
upload returns 201, delete returns 204, download streams bytes.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /documents?employeeId= | 200 — metadata list, newest first | 400 missing param · 401 |
| POST | /documents | 201 — metadata (multipart/form-data: employeeId, documentType, file) | 400 validation · 404 unknown employee |
| GET | /documents/{id}/download | 200 — bytes + `Content-Disposition` (UTF-8 filename) | 404 unknown id or missing file |
| DELETE | /documents/{id} | 204 | 404 · 403 (MANAGER) |

### Rules

- Allowed types: `RESUME, OFFER_LETTER, ID_PROOF, CERTIFICATE, EXPERIENCE_LETTER, OTHER`.
- Extension whitelist: pdf, doc, docx, jpg, jpeg, png. Max size 5 MB (service guard;
  Spring multipart cap 6 MB). Empty files rejected.
- Bytes are stored under `app.documents.storage-dir` (env `DOCS_STORAGE_DIR`, default
  `./uploads/documents`) with UUID-prefixed sanitized names — original names and any
  user-controlled path never touch disk; resolved paths are re-verified to stay inside
  the storage directory.
- Downloads stream through `StreamingResponseBody` and always return the original file
  name via `filename*=UTF-8''`. Delete removes metadata and the stored file (a stale
  orphan file never fails the request).

## Phase 12 — Notifications ✅

Every endpoint is scoped to the JWT principal — there is no cross-user access, so all
authenticated roles (including EMPLOYEE) may read and clear their own rows. List payloads
use the standard envelope; PATCH returns 200.

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | /notifications | 200 — latest 50 rows, newest first | 401 |
| GET | /notifications/unread | 200 — `{ unread: n }` badge count | 401 |
| PATCH | /notifications/{id}/read | 200 — row (idempotent) | 404 unknown id **or someone else's row** |
| PATCH | /notifications/read-all | 200 — `{ unread: nChanged }` | 401 |

### Rules

- Rows are addressed to **login USERS**. The User↔Employee link resolves by matching
  `EMPLOYEES.EMAIL` (unique) — no schema change; employees without a login silently
  receive nothing.
- Events emitted (same transaction as the triggering action, so a rollback never
  notifies): leave request **submitted/approved/rejected** (→ employee), onboarding
  **started** (→ employee), payroll payslip **PAID** (→ employee), performance review
  **SUBMITTED** (→ employee) and **ACKNOWLEDGED** (→ reviewer).
- Types: `SYSTEM, LEAVE, ONBOARDING, PAYROLL, PERFORMANCE, RECRUITMENT`.
- The bulk mark-read is native SQL (`read_flag = 1`) because READ_FLAG is NUMBER(1) —
  JPQL boolean literals bind as BOOLEAN and are rejected by Oracle mode.
- The frontend toolbar bell polls `/unread` every 30 s; mark-read actions share one
  badge signal so the count updates instantly without waiting for a poll.

## Phase 13 — Analytics ✅

All endpoints: **ADMIN, HR** only (same policy as the dashboard — org-wide figures).
Read-only; every response uses the standard envelope.

| Method | Path | Success |
|---|---|---|
| GET | /analytics/workforce | active/terminated headcount, avgTenureYears (1 dp), counts by department, employment type, and tenure cohort (<1y, 1–3y, 3–5y, 5+y) |
| GET | /analytics/funnel | total applications + per-job funnel (total, active, selected, rejected — active = not yet terminal) |
| GET | /analytics/interviews | status counters (completed/scheduled/cancelled) + result counters (pass/fail/onHold) + passRate over COMPLETED only (1 dp) |
| GET | /analytics/leave | current-year request counts per leave type (busiest first) + per status |
| GET | /analytics/payroll-trend | per-period payslip count and total net, **oldest first** (chart order) |
| GET | /analytics/performance | total reviews, average rating (1 dp, official ratings only), counts by status, rating distribution 1–5 |

### Rules

- Every grouping is computed in the database (GROUP BY + CASE sums); the service only maps
  rows. No entity lists are ever returned.
- Tenure cohorts and passRate are Java-side derivations over aggregate rows.
- Performance analytics count **official ratings only** (SUBMITTED/ACKNOWLEDGED) — a
  DRAFT's rating is reviewer notes and never enters average or distribution.
- Leave demand is keyed by request start date within the current calendar year.

## Phase 14 — Cross-cutting List Hardening ✅

Every list endpoint now shares one contract (new **Phase 14 convention**):

- **Envelope**: `PageResponse` — `{ content, page, size, totalElements, totalPages,
  first, last }` inside the standard `ApiResponse.data`.
- **Filters**: `search` (trimmed, blank → ignored, capped at 100 chars, case-insensitive
  across each list's natural text columns) and `status` (typed enum; an unknown value is a
  **400**, never a 500).
- **Paging**: `page` is 0-based (negatives clamp to 0), `size` clamps to [1, 100] with
  default 20. A far-out page returns empty `content` with `last: true` — never an error.
- **Determinism**: every list is deterministically ordered (newest-first for feeds,
  name/title asc for reference data, id desc/asc elsewhere), so pagination is stable.

| Method | Path | Filters | Notes |
|---|---|---|---|
| GET | /jobs, /candidates, /applications, /interviews | search, status, page, size | KPI strips now read `GET /applications/counts` (per-status totals, independent of paging) |
| GET | /leave/requests | status, search, page, size | search over employee name/code, type, reason |
| GET | /performance/reviews | employeeId, status, search, page, size | |
| GET | /onboardings | status, search, page, size | |
| GET | /documents | employeeId (required), search, page, size | missing/malformed `employeeId` → 400 |
| GET | /notifications | unread, search, page, size | personal feed; unread + search are SQL filters (Phase 17), totals are real `COUNT(*)` over the filtered set |
| GET | /departments/page, /designations/page | search, page, size | new admin paged views; the unpaged `/departments` and `/designations` remain for dialogs/pickers |
| GET | /employees | search, departmentId, status, employmentType, page, size, sortBy, sortDir | Phase 3 real SQL paging; sort whitelist; now also clamps via the shared policy |

**Unpaged by design** (bounded or reference data): `/leave/types`, `/employees/options`,
`/attendance/today`, `/attendance/month`, `/payroll/{year}/{month}`, and the analytics
endpoints.

**Error hardening:** unknown enum values (`?status=NOPE`) and malformed numeric/boolean
params return 400 with `"Invalid value for parameter: <name>"`; missing required query
params return 400 with `"Missing required parameter: <name>"` — previously both fell
through to a 500.

**Frontend contract:** services expose arrays as before; internally they request
`size=100` (the clamp max) and unwrap `data.content`, so existing components needed no
changes. The recruitment KPI strip switched to `/applications/counts` so it stays exact
under any page size.

## Phase 15 — Reports (CSV/PDF Exports) ✅

File downloads for the four main HR lists. **ADMIN, HR, MANAGER** (the read matrix of
the underlying lists). Responses are raw bytes — deliberately **outside** the standard
ApiResponse envelope — with `Content-Disposition: attachment` and a UTF-8 filename.

| Method | Path | Params | Notes |
|---|---|---|---|
| GET | /reports/employees.csv | search, status (optional) | same filters as the employees list; UTF-8 BOM for Excel |
| GET | /reports/employees.pdf | search, status (optional) | A4 landscape table |
| GET | /reports/payroll.csv | year, month (required) | one period's payslips; empty period → header-only file |
| GET | /reports/payroll.pdf | year, month (required) | |
| GET | /reports/attendance.csv | from, to (required, ISO dates) | `to` before `from` → 400 |
| GET | /reports/attendance.pdf | from, to (required, ISO dates) | |
| GET | /reports/leave.csv | status (optional) | unknown status → 400 (Phase 14 convention) |
| GET | /reports/leave.pdf | status (optional) | |

### Rules

- **CSV**: RFC 4180 (CRLF, comma-escaping, quote-doubling), UTF-8 with BOM so Excel
  detects encoding, and **formula-injection sanitisation** — a leading `= + - @` in any
  cell (e.g. a phone number or a reason like `=HYPERLINK…`) is prefixed with `'` per the
  OWASP guidance, so the export can never execute formulas when opened in a spreadsheet.
- **PDF**: OpenPDF (maintained LGPL/MPL fork of iText 4), A4 landscape, title +
  generation timestamp + grey header row; valid `%PDF-…%%EOF` documents.
- Exports are point-in-time reads; they use the same repository queries as the UI lists
  so numbers always match the screens.
- Frontend: shared `ReportService` fetches the bytes as a blob and triggers a browser
  download; export buttons on Employees, Payroll (selected period), Attendance (viewed
  month) and Leave (selected status tab) pass the currently-applied filters.

## Phase 16 — AI Insights (Skill Extraction & Match Scoring) ✅

Deterministic, explainable recruitment insights — a curated skill dictionary with
aliases, word-boundary matching, and a transparent score formula. No external AI
service required (self-contained workspace by design); the engine sits behind two
calls so a real LLM provider could replace it without touching the API. Reads only —
**ADMIN, HR, MANAGER** (the recruitment read matrix).

| Method | Path | Body/Params | Notes |
|---|---|---|---|
| POST | /ai/resume-skills | `{ "text": "..." }` | stateless extraction; nothing is persisted |
| POST | /ai/resume-files | multipart `file` (.pdf/.txt) | PDF text extraction via OpenPDF; unsupported type → 400 |
| GET | /ai/job-matches/{jobId} | — | required skills (from title+description) + ranked candidates |

### Rules

- **Dictionary**: ~28 canonical skills (Java, Spring Boot, Oracle SQL, Angular, React,
  Docker, Kubernetes, Tally, …) each with lowercase aliases; matching is
  case-insensitive on word boundaries (`java` never matches inside `javascript`).
- **Suppression is span-based**: an alias match is dropped only when its span lies
  strictly inside a longer match of another skill (`sql` inside `oracle sql`).
  Substring pruning was tried and rejected — "JavaScript" contains "java" as a string.
- **Score** = floor(100 × matched / required). `requiredSkills` come only from what the
  job text actually says; `matchedSkills`/`missingSkills` are echoed per candidate so
  every score is auditable. Ties break by fewer missing skills, then name. Candidates
  with no extractable skills are omitted.

## Phase 17 — SQL Paging Hardening (DB-Side Filters) ✅

The Phase 14 slice-paged endpoints that face unbounded growth were moved to real
SQL paging: the page window, filters, and totals are computed by the database,
not by loading every row into memory. Scale-critical lists only — bounded data
(onboarding: one row per hire; performance: one per review cycle; documents:
per-employee) deliberately keeps the Phase 14 slice approach.

| Endpoints | Mechanism | Notes |
|---|---|---|
| GET /notifications | native SQL paging | `unread` binds as `0` against `NUMBER(1)` READ_FLAG — never a Boolean (the Phase 12 Oracle-mode hazard); search is an escaped LIKE over title+message; ordering `id DESC` in SQL |
| GET /jobs | paged JPQL projection | status + escaped search over title/location/department, per-job application count via subquery, `lower(title)` ordering |
| GET /candidates | paged JPQL projection | status + search over name/email/skills (NULL-safe), newest-first with application counts, explicit count query |
| GET /applications | paged JPQL projection | status + search over candidate/job/remarks, `id` ascending |
| GET /interviews | paged JPQL projection | status + search over candidate/job/interviewer (NULL-safe), `id` ascending |
| GET /leave/requests | paged JPQL fetch-join | status + search over employee/code/type/reason, detail joins kept for lazy-safe mapping; count twin without fetch joins |

### Conventions (all new queries)

- Typed binds only: numeric IDs/flags, enums, escaped VARCHAR patterns. No null-Boolean
  flag binds, which Oracle (and H2 `MODE=Oracle`) reject against `NUMBER(1)` columns.
- Search patterns are built by `common.SqlPaging.likeEscape`: lower-cased, `\` `%` `_`
  escaped, used with `LIKE :pattern ESCAPE '\'` — a literal `%` from a client matches
  nothing instead of everything.
- Count queries are filter-exact (explicitly declared where Spring cannot derive a
  correct one through `join fetch` or non-null default filters), so `totalElements`
  always describes the whole filtered set at any page size.
- Clamp policy unchanged (Phase 14): `page` 0-based negatives→0, `size` 1..100,
  `search` trimmed and capped at 100 chars.

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
