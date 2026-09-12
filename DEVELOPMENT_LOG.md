# HRGenius — Development Log

Chronological record of decisions, bugs and fixes.

## 2026-09-11 — Project kickoff

### Environment audit

- Java 21.0.10 LTS ✅ (spec requires 17+)
- Node v26.3.1 / npm 11.16.0 ✅
- Maven ❌ not installed → decision: Maven Wrapper (`mvnw`) committed to repo
- Angular CLI ❌ not global → decision: CLI as devDependency, run via npm scripts
- Docker ❌, Oracle ❌ (port 1521 closed, no sqlplus)

### Key decision: Oracle-first with H2 dev profile

No Oracle instance exists on this machine. Options considered: install Oracle 23ai Free (large download, elevation, interactive installer risk), ask user for a remote instance (none available), or emulate. **Decision:** the codebase stays Oracle-first — `ojdbc11` driver, Hibernate `OracleDialect`, Flyway migrations written in Oracle syntax — but the default `dev` profile runs on H2 in Oracle compatibility mode so development starts immediately. Switching to real Oracle later requires only `SPRING_PROFILES_ACTIVE=oracle` plus `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`. User confirmed this choice.

### Other decisions

- Modular monolith, one Maven module; vertical feature packages instead of layer-only packages.
- `ApiResponse<T>` envelope + single `@RestControllerAdvice` error shape.
- springdoc OpenAPI for live Swagger docs alongside `API_DOCUMENTATION.md`.
- Docs-first: ARCHITECTURE / ROADMAP / DATABASE_DESIGN / API_DOCUMENTATION / DEVELOPMENT_LOG created before code.

### Phase 0 — Foundation

- Scaffolded `backend/` (Spring Boot 3.5.x, Java 21, Web/Data JPA/Security/Validation/Flyway/springdoc) and `frontend/` (Angular 20, Angular Material).
- Flyway `V1__init_schema.sql` + `V2__seed_data.sql` (demo users, departments, designations, employees).
- Common layer: `ApiResponse`, `ApiError`, `GlobalExceptionHandler`, `HealthController` with DB probe.
- CORS for `http://localhost:4200`, H2 console enabled in dev.
- Frontend: Material shell (sidebar/topbar), health check on dashboard, dev proxy `/api → :8080`.

#### Phase Summary (Phase 0)

- Implemented: full scaffold + health API + migrations + seed data + Angular shell
- APIs: `GET /api/v1/health`
- DB: 15 tables, constraints and indexes per DATABASE_DESIGN.md; 4 demo users
- Known issues: none at phase close
- Result: PASS (backend compiles, tests pass, boots on H2; Angular builds and proxies to API)

## 2026-09-11 — Phase 1: Authentication & Authorization

### What was built

**Backend** (`com.hrgenius.auth` + config):
- `User` entity on the existing USERS table; `Role` enum (ADMIN/HR/MANAGER/EMPLOYEE).
- `V3__auth_token_version.sql`: `TOKEN_VERSION` column + role index — enables **stateless
  logout**: POST /auth/logout bumps the version, so all previously issued JWTs die instantly
  without a blacklist. The filter re-loads the user per request and checks ENABLED + version.
- `JwtService` (JJWT 0.12, HS384, 60 min, claims sub/uid/role/ver), `JwtAuthenticationFilter`,
  `AuthService`, `AuthController` (login / me / logout), DTOs with Bean Validation.
- `SecurityConfig`: stateless chain, public paths externalized to `application.yml`,
  filter-level CORS, `@PreAuthorize` method security, and custom 401/403 handlers that emit
  the standard `ApiError` JSON (Spring's default would be a bare 403 for unauthenticated).
- `AdminGuardController` (`GET /api/v1/admin/only`) as the RBAC smoke endpoint.

**Frontend:**
- `AuthService` (signals + localStorage, JWT `exp` check), functional `authGuard`/`guestGuard`
  with `?returnUrl=`, login page (Material, reactive form, inline API error display),
  user chip + sign-out menu in the toolbar, 401 auto-logout in the error interceptor
  (login endpoint excluded to prevent redirect loops).

### Bugs found & fixed (ERROR → ROOT CAUSE → FIX → VERIFY)

1. **All four seed users shared one BCrypt hash** — documented passwords would all have
   failed. Generated real per-password hashes with spring-security-crypto and fixed V2
   (safe: dev DB is in-memory and rebuilt each boot, so the Flyway checksum never persists).
2. **403 instead of 401 for anonymous requests** — Spring Security's default entry point.
   Fixed with an explicit `AuthenticationEntryPoint` returning the standard 401 envelope.
3. `AuthenticationEntryPoint`/`AccessDeniedHandler` lambda parameter types — the entry point
   takes `AuthenticationException`, the handler the *web* `AccessDeniedException`; let the
   compiler infer instead of annotating `Exception`.
4. An outdated Phase 0 test expected 404/500 for unknown API paths; under real security
   the correct answer is 401 (authenticate before revealing route existence). Updated the test.
5. Frontend `AuthService.login()` declared `Observable<AuthUser>` but returned the envelope
   (TS2322) — now maps to the unwrapped user after storing the session.
6. Material button icon-slot warning (NG8011) from an `@if` with mixed root nodes inside the
   submit button — wrapped in a single `<span>`.

### Verification

- Backend: `mvnw test` → **13/13** (11 auth integration + 2 health), boots on H2 Oracle-mode.
- Live curl checks: login ✅, `/me` ✅, wrong password 401 ✅, garbage token 401 ✅,
  RBAC 200 (ADMIN) / 403 (EMPLOYEE) ✅.
- Frontend: `ng build` clean; `ng test` → **20/20**.
- Live UI (preview tab): `/` redirects to `/login` when signed out; admin login lands on the
  dashboard with the user chip; sign-out returns to `/login` and server-invalidates the token;
  HR account login works with its distinct credentials.

#### Phase Summary (Phase 1)

- APIs: POST /auth/login · GET /auth/me · POST /auth/logout · GET /admin/only (smoke)
- DB changes: V3 `USERS.TOKEN_VERSION` (default 0, NOT NULL) + `IX_USERS_ROLE`
- Tests: backend 13/13, frontend 20/20
- Known issues: none at phase close
- Result: PASS
- Next: Phase 2 — Admin dashboard with real aggregated stats

## 2026-09-11 — Phase 2: Admin Dashboard

### What was built

**Backend — domain foundations** (`employee`, `department`, `recruitment`, `leave`,
`attendance`, `payroll` packages): entities for Employee, Department, Designation, Job,
Candidate, JobApplication, Interview, LeaveType, LeaveRequest, Attendance, Payroll —
all mapped to the existing V1 tables, enum-backed status columns, lazy FK associations.
These are the bases Phase 3+ builds CRUD on.

**Backend — analytics package:**
- `DashboardStats` nested-record DTO (KPIs, distribution, trend, pipeline, attendance,
  leave, payroll, recentHires, pendingApprovals, upcomingInterviews).
- `DashboardService`: one `@Transactional(readOnly)` method; **all grouping/counting in
  the database** via JPQL aggregations (HQL year()/month() work on Oracle and H2).
- `DashboardController`: `GET /api/v1/dashboard/stats` with `@PreAuthorize(hasAnyRole
  ('ADMIN','HR'))`.

**Seed data (V2 extended):** 4 candidates, 4 applications across pipeline stages, an
upcoming + a completed interview, 5 days × employees of attendance, 4 leave requests
(2 pending for the approval queue), last-month payroll for all employees, 2 performance
reviews, and **EMP007 hired 10 days ago** so new-hire KPI/trend/recent-hires are alive.
All dates are relative to the run date.

**Frontend:**
- `DashboardService` + `dashboard.models.ts` (mirror of backend DTO tree).
- Reusable `BarChartComponent` (zero chart dependencies, CSS bars, tooltips,
  aria-labels, `@empty` state, optional totals).
- Dashboard rewrite: 6 KPI cards, 4 chart cards, payroll strip, 3 queues
  (recent hires / pending approvals / upcoming interviews), compact system strip.
  Explicit `403` restricted state and offline error state; Refresh button.

### Bugs found & fixed

1. Test-helper bug: login response was read as `AuthResponse` directly instead of the
   `ApiResponse` envelope → token was null → 401 instead of 403/200 in dashboard tests.
   Fixed by parsing `data.token` (same pattern as the auth tests).
2. `EXTRACT(year from …)` JPQL via `function()` is not valid HQL — replaced with
   HQL `year()`/`month()` which work on both Oracle and H2.
3. All seed hires were >30 days old → empty new-hires KPI, trend and recent-hires queue.
   Added EMP007 (relative date) + updated test expectations (7 employees).
4. Frontend: `monthName` helper unreachable from template (file-local) → component method;
   missing `CommonModule` import for the `currency` pipe; `matTooltip` requires a string;
   `Bar.total` type widened via `|| null`.

### Verification

- Backend: **18/18** tests (5 dashboard: aggregates computed from seed data, RBAC for
  ADMIN/HR/MANAGER/EMPLOYEE, unauthenticated 401).
- Live curl: `/dashboard/stats` returns exact seed-derived numbers.
- Frontend: `ng build` clean; `ng test` → **22/22**.
- Live UI: HR session renders all KPIs (7/7/1/2/4/2), charts with real distributions,
  queues populated with seeded names, API UP strip. (Screenshot capture flaked in this
  environment; DOM snapshot + tests are the verification of record.)

#### Phase Summary (Phase 2)

- APIs: GET /api/v1/dashboard/stats (ADMIN, HR)
- DB changes: none (schema unchanged; seed data extended in V2)
- Tests: backend 18/18, frontend 22/22
- Known issues: manager/employee get a "restricted" card (team-scoped dashboard planned)
- Result: PASS
- Next: Phase 3 — Employee management (CRUD, search, filter, pagination)

## 2026-09-11 — Phase 3: Employee Management

### What was built

**Backend:**
- `common.PageResponse<T>`: stable pagination envelope (decoupled from Spring's
  PageImpl JSON, which changes shape between versions).
- `EmployeeDto` (validated request + rich `Response` projection with resolved
  department/designation/manager names — entities never cross the API boundary).
- `EmployeeSpecifications`: composable JPA Specifications (search across name/code/email,
  department, status, employment type).
- `EmployeeService`: whitelist-validated sorting, size capped at 100, uniqueness checks,
  FK resolution, designation↔department consistency check, self-manager block,
  **soft delete** (status → TERMINATED, blocked while the employee still manages others).
- `EmployeeController`: full CRUD with RBAC — list/get ADMIN+HR+MANAGER,
  create/update ADMIN+HR, delete ADMIN only.
- Read-only `GET /departments` and `GET /designations?departmentId=` for the dialog
  dropdowns (full department CRUD is Phase 4).

**Frontend:**
- `EmployeesComponent`: Material data table with matSort, paginator, debounced search
  (350 ms), status/type/department filters, server-driven everything; role-aware actions
  (edit ADMIN/HR, deactivate ADMIN only); offline + empty states.
- `EmployeeDialogComponent`: add/edit form — department change reloads + resets the
  designation list (backend validates the pairing too); backend field errors map onto
  form fields; 409 conflict messages shown inline.
- `ConfirmDeleteDialog`: explicit soft-delete confirmation.
- Route `/employees` + sidebar phase gate bumped to 3.

### Bugs found & fixed (all via the ERROR → ROOT CAUSE → FIX cycle)

1. **Create → 409 CK_EMP_GENDER:** `Employee.gender` was missing
   `@Enumerated(EnumType.STRING)` — Hibernate silently used ORDINAL, binding `2` into a
   VARCHAR2 column holding enum names. Invisible until the first write with a gender set.
2. **Search returned zero rows:** Hibernate 6 renders `CriteriaBuilder.like` without an
   explicit escape as `like ? escape ''`; H2 treats the empty escape as *literal* matching,
   so `%rohan%` matched nothing. Fixed with the three-arg `like(expression, pattern, '\\')`.
   (Both bugs were invisible in the dashboard phase because those paths never ran.)
3. Test expectation bug: EMP001 has no manager (top of hierarchy) and Jackson `non_null`
   omits the field — the projection test now uses EMP002 (has manager "Rahul Verma").
4. Angular template: `<textarea />` self-closing is invalid (NG5002) → explicit closing tag.

### Verification

- Backend: **34/34** tests (16 new: RBAC matrix incl. manager-can-list-but-not-create,
  pagination/sort math vs seed, search, filters, duplicate 409, validation 400 map,
  FK 404, cross-department designation 400, soft-delete + manage-guard 409).
- Live curl: pagination (`totalElements 7, totalPages 3`), search, departments endpoint.
- Frontend: `ng build` clean; **25/25** tests.
- Live UI: table renders 7 employees → typed "rohan" → instant single-row result →
  Add dialog: department Engineering → designation list cascades → created EMP008
  Meera Joshi → table shows "1 – 8 of 8". Session-expiry redirect also observed working
  (expired HR token bounced to /login with returnUrl).

#### Phase Summary (Phase 3)

- APIs: employees CRUD (5 endpoints) + departments/designations read endpoints
- DB changes: none (existing schema; no migration needed)
- Tests: backend 34/34, frontend 25/25
- Known issues: manager picker in the dialog is a placeholder (needs employee options
  endpoint — planned with the self-service phase); list is org-wide for MANAGER
  (team scoping deferred)
- Result: PASS
- Next: Phase 4 — Department & designation management (full CRUD, employee counts)

## 2026-09-11 — Phase 4: Departments & Designations

### What was built

**Backend** (`department` package):
- `DepartmentDto` (request + `Detail` response with `managerName` + `employeeCount`).
- Grouped count queries in `DepartmentRepository`/`DesignationRepository`
  (`select d.id, count(e) … group by d.id`) — lists never count employees N+1.
- `DepartmentService`: name uniqueness; manager must reference an existing employee;
  **delete guard** — 409 while the department still has employees.
- `DesignationService`: title unique per department; FK 404; delete guard — 409 while
  employees hold the designation.
- `DepartmentController` (replaced the Phase 3 read-only one): full CRUD for both
  resources — reads ADMIN/HR/MANAGER, writes ADMIN only.
- `GET /api/v1/employees/options?search=` — lightweight manager-picker projection
  (`id, employeeCode, fullName, departmentName`, TERMINATED excluded). Used by the
  department dialog (manager) and now the employee dialog (replacing the Phase 3
  placeholder).

**Frontend:**
- `DepartmentsComponent`: two Material tables on one page — departments (manager +
  employee count) and designations (department + count); ADMIN sees Add/Edit/Delete,
  HR/MANAGER get read-only tables.
- `DepartmentDialog` (name, manager select fed by `/employees/options`, description),
  `DesignationDialog` (title, department, description), and `DeleteGuardedDialog`,
  which warns with the live employee count when a delete will be rejected.
- Employee dialog manager picker now lists real employees instead of the placeholder.
- Route `/departments` + sidebar phase gate bumped to 4.

### Verification

- Backend: **48/48** tests first try (14 new: writes ADMIN-only, duplicate name 409,
  unknown manager 404, department-delete guard 409, designation-delete guard 409,
  delete success 204 when empty, employee counts, designation CRUD + department filter).
- Live curl: `/departments` returns manager names + counts; options endpoint filters.
- Frontend: `ng build` clean; **25/25** tests.
- Live UI (admin): created "Marketing" via the dialog with manager Vikram Singh →
  row appeared with 0 employees ("Departments (5)"); attempted to delete Engineering →
  confirm dialog warned "4 employee(s)", server answered 409, row intact (network log:
  `POST /departments → 201`, `DELETE /departments/1 → 409`).

#### Phase Summary (Phase 4)

- APIs: departments CRUD (5 endpoints) + designations CRUD (4) + GET /employees/options
- DB changes: none (schema unchanged)
- Tests: backend 48/48, frontend 25/25
- Known issues: none at phase close
- Result: PASS
- Next: Phase 5 — Recruitment (jobs, candidates, applications, interviews)

## 2026-09-12 — Phase 5: Recruitment

### What was built

**Backend** (`recruitment` package):
- `RecruitmentDto`: request/response records for all four resources; entities stay internal.
- `RecruitmentService`: jobs (create/update/delete with a **409 guard while applications
  exist**), candidates (service-level email uniqueness — no DB constraint), applications
  (OPEN-job rule, duplicate 409 per UK_APP_CAND_JOB, **explicit transition map**
  APPLIED→SCREENING→SHORTLISTED→INTERVIEW→SELECTED/REJECTED with terminal stages;
  illegal moves are 409; candidate status kept in sync), interviews (schedulable only for
  SHORTLISTED/INTERVIEW, past dates 400, one live SCHEDULED interview per application;
  **scheduling a SHORTLISTED application auto-advances it to INTERVIEW**; reschedule/
  complete-with-feedback/cancel — completing never auto-moves the pipeline, HR decides).
- `RecruitmentController`: 13 endpoints; reads ADMIN/HR/MANAGER, writes ADMIN/HR.
- Test infra: `junit-platform.properties` pins class order by name — the dashboard suite's
  exact seed-derived counts must run before suites that create rows.

**Frontend** (`recruitment` package):
- One page, four tabs (Jobs / Pipeline / Candidates / Interviews) + KPI strip.
- Pipeline strip shows all six stages with live counts; stage chips colour-coded.
- Dialogs: job post/edit, candidate add, apply, **Move** (lists only legal next stages;
  terminal stages show an explanation), interview schedule/reschedule (eligible
  applications filtered to SHORTLISTED/INTERVIEW, employee-options interviewer picker,
  datetime-local), feedback/complete, and guarded delete/cancel confirms.
- Tab selection preserved across reloads via `[(selectedIndex)]` signal.

### Bugs found & fixed

1. **Derived-query misplacement:** I initially put `countByJob_Id` on `JobRepository`
   (root entity `Job` has no `job` property) and left `existsByApplicationIdAndStatus` in
   `JobApplicationRepository` (`JobApplication` has no `application`). Spring Data validates
   queries at context startup, so *all 73 tests errored* — fixed by keeping each method on
   its owning repository with explicit `_` nesting for nested-id paths.
2. Frontend build errors: `mat-tab-group` used as an attribute on `<nav>` (it is a
   component); `forkJoin` typed on envelopes instead of unwrapped `data`; `Map<ApplicationStatus,…>`
   vs `string` template keys; missing `JobApplication` import in the service; `MoveDialog`
   missing `MatSelectModule` (added via a str_replace that also corrupted the file — repaired
   and re-verified).
3. **Live UI bug:** closing a dialog reloaded data but bounced the user back to the first
   tab — fixed with the `selectedTab` signal.
4. **Interviewer dropdown rendered "()":** my `EmployeeOption` interface invented a shape
   (`fullName`/`employeeCode`) that didn't match the real `/employees/options` contract
   (`{id, label}`) — aligned the model to the endpoint instead of the endpoint to the model.

### Verification

- Backend: **73/73** tests (25 new: full pipeline walk, illegal-transition 409 with exact
  message, terminal-stage 409, duplicate application/candidate 409, OPEN-job 400, guards,
  interview lifecycle incl. auto-advance + second-live-interview 409 + cancel-then-reschedule
  409, RBAC matrix).
- Live curl: jobs list with counts, applications projection, `PATCH /applications/4/status`
  → 409 `Cannot move application from APPLIED to SELECTED`.
- Frontend: `ng build` clean; **29/29** tests.
- Live UI (admin): moved Sanjay APPLIED→SCREENING (strip APPLIED 0 / SCREENING 2),
  scheduled an ONLINE interview for Fatima with Rahul Verma → new row appeared and her
  application auto-advanced SHORTLISTED→INTERVIEW (strip INTERVIEW 2), COMPLETED/PASS
  interview shows no actions.

#### Phase Summary (Phase 5)

- APIs: jobs (4), candidates (4), applications (3 incl. PATCH status), interviews (5) = 16
- DB changes: none (existing schema/seed)
- Tests: backend 73/73, frontend 29/29
- Known issues: offers/onboarding (SELECTED → HIRED) lands in Phase 6; manager sees
  recruitment read-only (team scoping deferred)
- Result: PASS
- Next: Phase 6 — Onboarding workflow (candidate → employee → completion %)

## 2026-09-12 — Phase 6: Onboarding Workflow

### What was built

**Backend** (`onboarding` package):
- `Onboarding` entity on the existing ONBOARDINGS table; `V4__onboarding_application_link.sql`
  adds nullable `APPLICATION_ID` (unique + FK) linking each record to the recruitment
  application that produced it.
- `ChecklistItem` record + `ChecklistConverter`: the checklist lives in the CHECKLIST
  CLOB as JSON; a null/empty column reads as the default 8-item template (all unchecked),
  so pre-existing rows stay meaningful.
- `OnboardingService`:
  - `startFromApplication` — SELECTED-only (else 409), one per application, creates the
    Employee (next free `EMP###`, name split, generated unique `@hrgenius.local` email,
    job's department, ACTIVE, FULL_TIME), marks the candidate HIRED, opens PENDING/0%.
  - `startForEmployee` — same record for an existing employee, one per employee (409).
  - `updateChecklist` — index-validated toggle; completion % recomputed (done/total) and
    status derived: 0 → PENDING, partial → IN_PROGRESS, all → COMPLETED.
- `OnboardingController`: 4 endpoints; reads ADMIN/HR/MANAGER, writes ADMIN/HR.
- Seed: EMP007 mid-onboarding at 50% (4 of 8 done) so the page is alive on boot.

**Frontend** (`onboarding` package):
- Page with KPI cards, two start buttons, progress cards (name, code, department, job,
  status chip, animated progress bar, joining date) and an expandable interactive
  checklist — each checkbox PATCHes and replaces the record in place (no reload).
- `StartOnboardingDialog` (two modes): application picker filtered to SELECTED, or
  employee options picker, plus optional joining date.
- Route `/onboarding` + sidebar phase gate bumped to 6.

### Bugs found & fixed

1. **CandidateRequest forced pipeline status:** the create-candidate DTO required a
   `status` field the service ignores (always starts NEW) — my Phase 5 test helper sent
   `CandidateStatus.NEW`, masking it. Removed the field from the contract entirely
   (status is pipeline-owned); Phase 5 helper updated; all 25 recruitment tests still green.
2. **Non-numeric path variable → 500:** `PATCH /onboardings/null/checklist` (id came from
   the failed create above) surfaced a `MethodArgumentTypeMismatchException` that fell
   through to the 500 handler. Added a dedicated handler → 400. Hardening that Phases 0–5
   never noticed.
3. First frontend draft smuggled an import-alias hack and an empty component — caught in
   self-review and rewritten cleanly before first build (which then passed first try).

### Verification

- Backend: **84/84** tests (11 new: SELECTED-only 409, 404, full conversion asserting the
  generated `EMP008`, duplicate 409, checklist math 12.5% → 100% with status transitions,
  invalid index 400, employee-origin path + duplicate 409, list, RBAC matrix).
- Live curl: `/onboardings` returns the seeded 50% record; checklist toggle 50% → 62.5%.
- Frontend: `ng build` clean; **29/29** tests.
- Live UI (admin): Rohan's card at 62.5% → expanded checklist shows correct item states →
  toggled "Payroll & tax details submitted" → **75% in place**, status stays IN_PROGRESS;
  Start-from-pipeline dialog renders with the SELECTED-only hint (empty list is correct:
  no live SELECTED applications).

#### Phase Summary (Phase 6)

- APIs: /onboardings (4 endpoints)
- DB changes: V4 (ONBOARDINGS.APPLICATION_ID unique FK) + V2 seed row
- Tests: backend 84/84, frontend 29/29
- Known issues: checklist item labels are not customizable per org (template fixed in
  code); no acceptance workflow after COMPLETED
- Result: PASS
- Next: Phase 7 — Attendance (check-in/out, monthly views)

## 2026-09-12 — Phase 7: Attendance

### What was built

**Backend** (`attendance` package):
- `AttendanceDto`: MarkRequest, RecordResponse, DaySummary, TodayResponse,
  MonthRow (per-employee roll-up + `days` day-of-month → status map), MonthResponse.
- `AttendanceService`:
  - `checkIn` — creates today's PRESENT record (409 if one exists per
    UK_ATT_EMP_DATE); `checkOut` — sets time + computes `workingHours`
    (minutes/60, 2 dp, negative clamps to 0; 409 without check-in or double check-out).
  - `mark` — upsert for corrections; working statuses keep timestamps,
    ABSENT/LEAVE/HOLIDAY clear them.
  - `today` — day strip + grouped status counts; `month` — every employee as a row
    (zero-record rows included), counters + hours accumulated per record,
    `attendancePercent = (present + 0.5·halfDay) / total × 100` at 1 dp.
- `AttendanceController`: 5 endpoints; reads ADMIN/HR/MANAGER, writes ADMIN/HR
  (User↔Employee link doesn't exist yet, so self-service check-in would be spoofable —
  deferred deliberately).

**Frontend** (`attendance` package):
- Today section: five status KPIs + record table with inline Check-in/Check-out
  buttons (busy-flag per employee; "Complete" when both stamps exist).
- Month grid: sticky employee column, one column per day, colour-coded single-letter
  cells (P/H/A/L/H), weekend shading, today outline, percent column with the
  P·H·A·L breakdown, prev/next month navigation, legend.
- Clicking a day cell (ADMIN/HR) opens the mark dialog (status select, prefilled
  context) → upsert → grid refresh.
- Route `/attendance` + sidebar phase gate bumped to 7.

### Bugs found & fixed

1. **Test expectation wrong, service right:** asserted Divya at 87.5% assuming a seed
   HALF_DAY — the seed gives HALF_DAY only to employees 2 and 5; her d-3 record is LEAVE,
   so (3+0)/4 = 75.0% was correct. Fixed the assertion (and re-derived Rahul's 66.7% by hand).
2. Corrupted service write mid-generation (duplicated lines, pseudo-code fragments) —
   caught by a targeted grep pass and rewritten cleanly; grep verified no artifacts.
3. First MonthRow design mutated immutable record copies (counter reassignment +
   `BigDecimal.add` results discarded) — restructured to build each row once from
   grouped records before any object is created.
4. Angular template can't see the global `String()` — added a `dayKey()` component
   helper for the day-map keys.
5. Unclosed `</option>` typo in the dialog template (caught at build).

### Verification

- Backend: **95/95** tests (11 new: full check-in→check-out lifecycle with computed
  hours, double check-in/out 409s, checkout-without-checkin 409, unknown employee 404,
  mark create-then-upsert, timestamp clearing, validation 400, today summary math,
  month roll-up math (75.0/66.7/83.3 asserted), RBAC matrix).
- Live curl: today empty-on-boot, month percentages, real check-in for EMP002.
- Frontend: `ng build` clean; **29/29** tests.
- Live UI (admin): today strip showed the live check-in (16:15) → clicked Check out →
  16:22 / 0.1h / "Complete"; clicked Vikram's day 8 (A) → mark dialog → saved PRESENT →
  cell flipped to P and his percent recomputed 50% → 75% in the grid.

#### Phase Summary (Phase 7)

- APIs: /attendance (5 endpoints)
- DB changes: none (existing ATTENDANCE table + seed)
- Tests: backend 95/95, frontend 29/29
- Known issues: no self-service check-in (needs a User↔Employee link); holidays are
  manual marks (no holiday calendar yet); no half-day check-in/out semantics
- Result: PASS
- Next: Phase 8 — Leave (types, balances, request → manager approval workflow)

---

## PHASE 8 — LEAVE MANAGEMENT

**Date:** 2026-09-12 · **Result: ✅ PASS**

### What was built

**Backend** (`com.hrgenius.leave`, 12 endpoints):
- Leave-type CRUD (409 on duplicate name, delete guarded while requests exist).
- Request lifecycle: create (PENDING) → approve/reject (records APPROVED_BY from the JWT
  principal) → cancel (PENDING only) → hard delete (decided only).
- Guards: end≥start (400), unknown employee/type (404), overlap against PENDING+APPROVED
  (409), yearly-limit check at submission **and re-checked at approval** so concurrent
  approvals cannot overdraw a type.
- `GET /balances/{employeeId}?year=` per-type used/remaining; `GET /summary` page KPIs with
  1-dp approval rate. Working-days display excludes weekends; balance accounting uses
  calendar days (both documented).

**Frontend** (`/leave`): KPI strip, status-filter chips, request table with approve/reject
icons (tooltip shows the decider on the reason cell), balance side panel with per-type
progress bars (turns red past 80% usage), leave-type cards with edit/delete, and three
dialogs (new request with employee + type + date pickers, type create/edit, delete confirm).

### Testing

- Backend: **110/110** — 15 new LeaveApiTest orders: create defaults (PENDING, 5.0 working
  days Mon–Fri), overlap 409, end<start 400, unknown employee/type 404, duplicate type 409 +
  create/delete-unused type, delete-with-requests 409, approve stamps approver, double-approve
  409, reject stamps decider, balance-exhaustion 409, cancel + re-cancel 409, delete rules,
  balances (2.0/10.0 vs seed), summary counts, RBAC matrix.
- Frontend: `ng build` clean · **29/29** tests.
- Live API: summary `{"pendingCount":2,...,"approvalRate":50.0}`, pending queue with names,
  Arjun balances 2/12 used · 10 remaining.

### Bugs the cycle caught (ERROR → ROOT CAUSE → FIX → VERIFY)

1. **Missing ApiResponse envelope** (found live in the browser): my controller returned raw
   DTOs while every other module wraps in `{success, message, data}`. XHRs were 200 but the
   page rendered empty — the frontend's `res.data` reads were undefined. Diagnosed via a
   temporary debug log + comparing the dashboard's interceptor flow. Fixed the controller to
   the house pattern; status codes now match the documented contract (201 create, 204 delete).
2. **Hibernate 6 HQL date arithmetic**: `sum(endDate - startDate + 1)` failed context startup
   with "Operand of + is not a TemporalAmount". Replaced SQL-side summation with a portable
   range query + Java-side `ChronoUnit.DAYS` summation.
3. Wrong test expectations vs seed (leave types are `CASUAL_LEAVE`, not "Casual Leave");
   two cascading-failure chains after order-1 aborted (null `createdRequestId` → /null/409s);
   a summary-rate assertion that ignored order-31's delete; a stray method header left in
   `LeaveService` by incremental edits (caught by `illegal start of expression`, file rewritten
   cleanly); `.set()` vs call-syntax on Angular signals; missing `MAT_DIALOG_DATA` import;
   balance picker's employee list never fetched (fixed in `reload()`).

### Phase Summary (Phase 8)

- APIs: /leave (12 endpoints)
- DB changes: none (existing LEAVE_TYPES / LEAVE_REQUESTS tables + seed)
- Tests: backend 110/110, frontend 29/29
- Known issues: no per-employee email notification on decisions (Phase 12); balances keyed by
  start-date year; half-day units not modeled (attendance has HALF_DAY, leave is full-day)
- Result: PASS
- Next: Phase 9 — Payroll (salary structure, monthly run, payslip, BigDecimal math)

---

## PHASE 9 — PAYROLL

**Date:** 2026-09-12 · **Result: ✅ PASS**

### What was built

**Backend** (`com.hrgenius.payroll`, 8 endpoints on /api/v1/payrolls):
- `POST /run` generates DRAFT payslips for every ACTIVE employee not yet in the period
  (existing rows skipped, never touched). Each new draft is pre-filled from that employee's
  latest payslip (or zeros), with net **recomputed**, never copied — the seed's deliberately
  inconsistent stored nets prove it (Rahul: stored 91,250 → recomputed 86,250).
- `PATCH /{id}/components` edits basic/allowances/deductions/tax (all @PositiveOrZero) and
  recomputes net = basic + allowances − deductions − tax, clamped at 0 (BigDecimal).
- Lifecycle `DRAFT → PROCESSED → PAID` with one-way transitions (409 otherwise); PAID is
  immutable (no edit, no re-pay, no delete — permanent financial record). Drafts/PROCESSED
  can be deleted; future periods rejected (400); unknown payslip 404.
- `GET /` runs overview (grouped period totals, newest first); `GET /{year}/{month}` period
  summary + payslips joined with employee/department, employee-code order.
- RBAC: reads ADMIN/HR/MANAGER; writes ADMIN/HR. EMPLOYEE role has no payroll access
  (sensitive financial data) — self-service payslips deferred until User↔Employee link.

**Frontend** (`/payroll`): period rail (each month with payslip count + total net, click to
load), summary KPI strip (payslips, total gross, total net, D/P/P counts), payslip table
with all five money columns, status pills, and per-status actions; Run dialog (month already
in the period rail is disabled), components dialog with live net preview, delete confirm.

### Testing

- Backend: **122/122** — 12 new PayrollApiTest orders: run creates ≥1 draft, second run
  creates 0 and skips everyone, draft count = run created, per-employee prefill equals the
  latest payslip with net recomputed (regex-parsed from both periods), periods overview lists
  both months, BigDecimal exact math (60123.45 + 5000.10 − 2000.20 − 3000.30 = 60123.05),
  negative components 400, unknown id 404, D→P→P lifecycle with re-process/re-pay 409,
  edit-PAID 409, delete-PAID 409 ("permanent"), draft delete 204, future period 400,
  invalid month 400, RBAC matrix.
- Frontend: `ng build` clean · **29/29** tests.
- Live API: seed period August 2026 → 7 payslips / ₹406,000 net.
- Live UI: ran September payroll from the dialog (rail gained "September 2026 · 7 payslips ·
  ₹4,00,750" — the ₹5,250 delta vs August exactly equals the two seed inconsistencies);
  verified Rahul's draft net ₹86,250 (recomputed); edited components 85000→85000.50 and tax
  8250→9000 with live preview 85,500.50, saved, net updated in table; marked PROCESSED then
  PAID ("Final", KPIs 6/0/1).

### Bugs the cycle caught (ERROR → ROOT CAUSE → FIX → VERIFY)

1. **Corrupted service/controller writes** (same failure mode as Phase 7): the first
   PayrollService write interleaved two drafts and produced pseudocode ("constructor
   placeholder", "re-used"); the controller write left a bare "becomes immutable;" statement.
   Both caught by immediate self-review greps and rewritten cleanly in one pass; file
   integrity grep before compiling.
2. **Loading spinner stuck on the payroll page** (found live): `reloadPeriods()` set
   `loading=true` and only cleared it in the no-period branch — when a period existed and
   `loadPeriod()` took over, the flag was never cleared, so the period rail showed "Loading…"
   forever. One-line fix (clear right after `periods.set`).
3. Test-design note: earlier suites add employees (onboarding conversions), so the suite
   asserts relative invariants (run twice → 0 created; skipped = created+skipped) and
   data-driven prefill rather than absolute seed counts.

### Phase Summary (Phase 9)

- APIs: /payrolls (8 endpoints)
- DB changes: none (existing PAYROLLS table + seed; Phase-2 entity reused)
- Tests: backend 122/122, frontend 29/29
- Known issues: no individual payslip PDF/export (Phase 15); no per-employee salary structure
  master (run baseline = last payslip); PAID immutability means corrections need a new run
- Result: PASS
- Next: Phase 10 — Performance: goals, KPIs, manager review, ratings

---

## PHASE 10 — PERFORMANCE

**Objective:** Goals, KPIs, manager reviews, and ratings — backend + frontend.

**Result: ✅ PASS**

| Check | Verification |
|---|---|
| Backend tests | **136/136** — 14 new: list/summary shape, create DRAFT defaults (rating null), duplicate employee+period 409, unknown employee/reviewer 404, edit-DRAFT round-trip, edit-SUBMITTED 409, rate 1–5 + narrative → SUBMITTED, rate 0/6 → 400, rate non-DRAFT 409, acknowledge SUBMITTED → ACKNOWLEDGED, double-acknowledge 409, delete-DRAFT 204, delete-SUBMITTED 409, self-review forbidden 409, RBAC matrix (MANAGER read-only, EMPLOYEE 403) |
| Frontend | `ng build` clean · **29/29** tests |
| Live API | Seed summary: avg 4.0 — the seed's DRAFT rating 3 correctly excluded, only official (SUBMITTED/ACKNOWLEDGED) ratings counted |
| Live UI | Acknowledge flipped Anita SUBMITTED → ACKNOWLEDGED in place · Rate & submit dialog: picked ★★★★☆ → Vikram DRAFT → SUBMITTED, avg 4/5 and ★★★★☆ ×2 recomputed · New review dialog: Divya Nair / reviewer Anita / 2026-H1 + goals → new DRAFT row, Total 2 → 3 |

**Implemented**
- **Backend:** 7 endpoints in `com.hrgenius.performance` on `/api/v1/performance`. One review
  per employee+period; one-way `DRAFT → SUBMITTED → ACKNOWLEDGED` lifecycle with edit/delete
  restricted to DRAFT; rate = rating 1–5 + narrative + submit in one transaction; acknowledge
  is employee sign-off. Summary returns counts per status plus average rating and 1–5
  distribution computed over **official ratings only** (SUBMITTED/ACKNOWLEDGED) — a DRAFT's
  rating is reviewer notes, never counted. Reads ADMIN/HR/MANAGER; writes ADMIN/HR.
- **Frontend:** KPI strip (total, drafts, submitted, acknowledged, average rating, star
  distribution), status filter chips, review table with employee/period/reviewer/rating
  stars/status/goals columns, and four dialogs — new review (employee + reviewer selects,
  period, goals), edit draft, rate & submit (star select), delete confirm. Sidebar shows
  "Phase 10 — Performance".

**Bugs the cycle caught** (full trail above): the rating dialog was missing `MatSelectModule`
(caught by `ng build`), a wrong aggregate expectation during testing (order-1's rating is
ACKNOWLEDGED, not draft — the query fix was right, my assertion was wrong), and two lost
dialog clicks to dev-server HMR races during live verification (re-click resolved; app code
unaffected).

**Docs updated:** `API_DOCUMENTATION.md` (7-endpoint contract + lifecycle rules),
`ROADMAP.md` (Phase 10 ✅), `DEVELOPMENT_LOG.md` (Phase 10 entry).

**Next:** Phase 11 — Documents: upload/download/delete, metadata, validation.
