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

---

## PHASE 11 — DOCUMENTS

**Objective:** Upload/download/delete, metadata, validation — backend + frontend.

**Result: ✅ PASS**

| Check | Verification |
|---|---|
| Backend tests | **146/146** — 10 new: upload 201 with full metadata (name/size/type/employee), disallowed extension 400, unknown type 400, unknown employee 404, empty file 400, list returns metadata without bytes, download round-trip (exact bytes + Content-Disposition + Content-Type), download unknown 404, delete removes metadata + file (download then 404), RBAC matrix (MANAGER read-only — upload AND delete forbidden; EMPLOYEE 403; ADMIN/HR full) |
| Frontend | `ng build` clean · **29/29** tests |
| Live API | curl upload → metadata `{id:1, Sneha Patil, OFFER_LETTER, 26 B}` · download returned exact uploaded bytes with `Content-Disposition: live-doc.pdf` |
| Live UI | Employee picker → Sneha's docs; chip counts update (Offer Letter · 1) · Upload dialog: file picker + type select + save → new row + chip appeared · Download button streams blob save · Delete confirm names the file → row + chip removed · console clean |

**Implemented**
- **Backend:** 4 endpoints in `com.hrgenius.document` on `/api/v1/documents`. Upload validates
  type (6 enum values), extension whitelist (pdf/doc/docx/jpg/jpeg/png), 5 MB cap, and the
  employee's existence; bytes are stored under `app.documents.storage-dir` with UUID-prefixed
  sanitized names and path-escape re-verification. Download streams via
  `StreamingResponseBody` with UTF-8 filename Content-Disposition. Delete removes metadata
  plus the stored file (orphans never fail the request). Reads ADMIN/HR/MANAGER; upload and
  delete ADMIN/HR — MANAGER is read-only by design.
- **Frontend:** employee picker with per-type count chips, documents table (icon, name, type
  pill, human-readable size, upload timestamp), blob-based download (the plain href cannot
  carry the JWT), upload dialog (file input with client-side size check, type select, busy
  state, server error surface), delete confirm dialog. Sidebar bumped to
  "Phase 11 — Documents".

**Bugs the cycle caught** (full trail above): a corrupted controller write (the recurring
failure mode — caught by grep, rewritten cleanly), a duplicate `app:` YAML key from an
in-place config insert (merged into one block), my own test expecting `fileSize` 29 vs the
actual 27-byte payload plus the cascade from `extractId` never running, an untestable-over-
HTTP oversized upload (Tomcat aborts the connection mid-request — documented, guard covered
by review), a self-contradicting RBAC expectation (the test asserted MANAGER upload 400
while the controller intentionally returns 403), one test-helper syntax error (`}` → `});`),
and a flaky stale-keep-alive login after the streaming test (one-shot retry).

**Docs updated:** `API_DOCUMENTATION.md` (4-endpoint contract + storage/validation rules),
`ROADMAP.md` (Phase 11 ✅), `DEVELOPMENT_LOG.md` (Phase 11 entry).

**Next:** Phase 12 — Notifications: events + unread badge.

---

## PHASE 12 — NOTIFICATIONS

**Objective:** Events + unread badge — in-app notifications delivered to login users,
backend + frontend.

**Result: ✅ PASS**

| Check | Verification |
|---|---|
| Backend tests | **152/152** — 6 new: principal-scoped list/unread (seed row visible to admin only, HR starts empty), mark-read clears badge + idempotent, read-all sweeps, foreign-owned id → 404 (not 403), unknown id 404, leave submit+approve delivers 2 LEAVE events to the linked employee, employee-without-login event silently dropped, all-roles RBAC incl. anonymous 401 |
| Frontend | `ng build` clean · **29/29** tests |
| Live API | Employee unread 0 → 2 after HR submitted + approved her leave (2027 dates, no seed/test interference) → read-all → 0; admin feed shows the seeded SYSTEM row; unknown id 404 |
| Live UI | Employee login shows badge "1" on the toolbar bell after a live event · bell navigates to /notifications · rows render type icon, unread dot, timestamp · mark-read flips the row and the badge in the same tick (shared signal) · badge hidden entirely at zero |

**Implemented**
- **Backend** (`com.hrgenius.notification`, 4 endpoints on `/api/v1/notifications`): entity on the
  existing NOTIFICATIONS table (no migration needed); rows are addressed to USERS with the
  User↔Employee link resolved by matching `EMPLOYEES.EMAIL` (unique) — employees without a
  login silently receive nothing. Reads/marks are scoped to the JWT principal (a foreign id
  answers 404, not 403 — rows outside your scope simply do not exist). Event emission joins
  the caller's transaction, so notifications never describe rolled-back work. Events wired:
  leave submitted/approved/rejected, onboarding started (both paths), payslip PAID, review
  SUBMITTED (→ employee) and ACKNOWLEDGED (→ reviewer).
- **Frontend**: toolbar bell with `matBadge` (30 s `/unread` polling), /notifications page with
  type-coloured icon tiles, unread dots, mark-read and mark-all-read. The badge lives in a
  shared service signal written by both the poller and the page, so counts update instantly
  without waiting for the next poll. Route `/notifications` + sidebar phase gate bumped to 12.

**Bugs the cycle caught** (ERROR → ROOT CAUSE → FIX → VERIFY)
1. **JPQL bulk boolean vs NUMBER(1)**: `update … set read = true` bound a BOOLEAN literal
   into the NUMBER(1) READ_FLAG column — H2 Oracle mode (and real Oracle) rejected it with
   "Values of types NUMERIC(1) and BOOLEAN are not comparable" → 500 on read-all. Rewritten
   as a native query with `read_flag = 1/0` literals, which is also what production Oracle
   requires.
2. Test bugs: the leave-approve endpoint is PATCH not POST (my helper sent POST → 500
   surfaced the real bug above), and counts were asserted absolutely although LeaveApiTest's
   own decisions deliver LEAVE events to the same user — switched to delta assertions.
3. Live-UI finding (not an app bug): Material's open account-menu renders a transparent
   full-screen backdrop that swallows toolbar clicks; combined with a 439 px-wide preview
   pane where the user chip visually overlaps the bell, coordinate-based clicks landed on
   the menu. DOM-verified the bell via real event dispatch; behaviour is correct on normal
   viewports.
4. Badge lag: the page and the bell each kept their own unread state, so marking read in
   the page took up to 30 s to clear the toolbar badge — replaced with one shared service
   signal written by both.

**Docs updated:** `API_DOCUMENTATION.md` (4-endpoint contract + event catalogue),
`ROADMAP.md` (Phase 12 ✅), `DEVELOPMENT_LOG.md` (this entry).

**Next:** Phase 13 — Analytics: real-data charts.

---

## PHASE 13 — ANALYTICS

**Objective:** Real-data charts beyond the dashboard's live aggregates — deeper,
chart-shaped views of workforce, hiring funnel, interviews, leave demand, payroll
trend, and performance health.

**Result: ✅ PASS**

| Check | Verification |
|---|---|
| Backend tests | **159/159** — 7 new (pure-seed, read-only, runs alphabetically FIRST): workforce composition (7 active, avg tenure 3.6 y, Engineering 4 leads, FULL_TIME 6/INTERN 1, tenure cohorts 1/1/3/2), funnel per job (2+2 applications, all active, none terminal), interview quality (1 completed PASS → passRate 100.0, DRAFT-exclusion pattern), leave demand (CASUAL 2 > SICK 1 = EARNED 1, busiest first), payroll trend (7 payslips / 406,000 net, oldest first), performance (avg 4.0 official-only, DRAFT rating 3 excluded), RBAC matrix (MANAGER+EMPLOYEE 403 on all six endpoints, anonymous 401) |
| Frontend | `ng build` clean · **29/29** tests |
| Live API | All six endpoints return exact seed numbers: workforce `{active:7, avgTenureYears:3.6}`, funnel `{totalApplications:4}`, interviews `{passRate:100.0}`, payroll-trend `[{2026-8, 7, 406000}]`, performance `{averageRating:4.0, byRating:[{4,1}]}` |
| Live UI | Admin: 5 KPIs (7 · 3.6 yrs · 4 · 100% · 4★), 8 cards, funnel table with per-job stage counts, 7 bar charts with correct row counts · Manager: "Analytics is restricted to ADMIN and HR" state · console clean |

**Implemented**
- **Backend** (`com.hrgenius.analytics`, 6 endpoints on `/api/v1/analytics`, ADMIN/HR only):
  `AnalyticsDto` record tree + `AnalyticsService` where **every grouping is computed in the
  database** (GROUP BY + CASE sums — e.g. the per-job funnel is one query with conditional
  SUMs for active/selected/rejected). New repository queries: employees per employment type,
  raw joining-date list (avg tenure + cohorts derived in Java), leave requests per type per
  year (busiest first), interview status/result groupings, per-job funnel. Reused existing
  aggregates where they existed (`ratingDistribution`, `periodTotals`, `countByDepartmentRaw`).
  Payroll trend is returned oldest-first for chart order (periodTotals is newest-first).
- **Frontend** (`/analytics`): `AnalyticsService.loadAll()` fetches all six sections in one
  parallel `forkJoin` round trip; KPI strip (active, avg tenure, applications, pass rate,
  avg rating); hiring funnel card (bar chart + stage-count table); headcount by department,
  employment mix, tenure cohorts, leave demand, and rating-distribution bar charts (reusing
  the zero-dependency `BarChartComponent`); interview outcome chips; net-payroll-per-period
  bars with money labels. Explicit 403-restricted and offline states; refresh button. Route
  `/analytics` + sidebar phase gate bumped to 13.

**Bugs the cycle caught** (ERROR → ROOT CAUSE → FIX → VERIFY)
1. **`avgTenureYears` long vs double**: DTO declared the field `long` while the service
   computed a rounded double (3.6) — compile error. Fixed the DTO to `double`.
2. **Stale V2 comment nearly poisoned a test**: the seed's payroll block says "all 6
   employees" but EMP007 is inserted BEFORE the payroll INSERT…SELECT, so the period has
   7 payslips / 406,000 net. My first assertion trusted the comment (6 / 349,750) and
   failed; the Phase 9 live verification (₹4,06,000) confirmed the payload was right and
   my expectation wrong. Rule: derive expectations from actual insertion order, not
   comments.
3. **Spring 6.2 `getStatusCode()` returns `HttpStatusCode`**, not `HttpStatus` — my RBAC
   helper's return type broke test compilation. Also: the first `mvnw test` run looked
   green only because grep filtered compile errors and surefire reports were stale —
   always confirm the new suite actually ran before trusting the total.
4. Template can't see file-local functions (Phase 2 lesson repeated): the funnel table
   called a local `titleCase` — exposed `statusLabel()` as a component method.

**Docs updated:** `API_DOCUMENTATION.md` (6-endpoint analytics contract), `ROADMAP.md`
(Phase 13 ✅), `DEVELOPMENT_LOG.md` (this entry).

**Next:** Phase 14 — Cross-cutting search/filter/pagination hardening.

## PHASE 14 — LIST HARDENING

**Goal:** one consistent contract for every list endpoint — PageResponse envelope,
`search` + `status` filters, clamped pagination, deterministic order, and 400 (never
500) for bad input — without breaking the frontend that consumed raw arrays.

**Design:**
- `common.Lists`: shared policy helper — `cleanSearch` (trim, blank→null, cap 100),
  `cleanPage` (negatives → 0), `cleanSize` (default 20, max 100), `containsTerm`
  (case-insensitive contains predicate), and `page(rows, page, size)` (slice paging over
  deterministic projections with honest totals).
- Endpoint upgrades: jobs/candidates/applications/interviews (search+status+paging,
  stable sort), leave/requests (search over employee/type/reason + status),
  performance/reviews (status+search), onboardings (status+search), documents (search
  over type/file name + paging, employeeId stays required), notifications (unread+
  search+paging over the personal feed), departments/designations got new
  `/page` admin views while the unpaged lists remain for dialogs and pickers.
- New `GET /applications/counts`: per-status application totals for KPI strips — strips
  no longer derive counts from paginated arrays.
- Employees (Phase 3) already had real SQL paging + sort whitelist; adopted the shared
  clamp policy and stays the reference implementation.
- **Deliberately unpaged:** leave/types, employees/options, attendance today/month,
  payroll per period, analytics — all bounded or reference data.

**Frontend:** shared `Page<T>` model; every affected service now requests `size=100`
(the clamp max) and unwraps `data.content`, so components keep their array semantics
unchanged. Recruitment's pipeline KPI switched to `/applications/counts`. The
RecruitmentService spec was extended for the envelope unwrap + counts endpoint.

**Bugs the cycle caught** (ERROR → ROOT CAUSE → FIX → VERIFY)
1. **Filter-after-page in notifications**: my first draft paged the DB query and then
   filtered `unread`/`search` in Java — content shrank while totals described the wider
   set. Fixed by filtering before paging (in-memory over the personal feed). The SQL-
   paging alternative was rejected on purpose: binding a `Boolean` against the
   NUMBER(1) READ_FLAG column is exactly the Oracle-mode hazard Phase 12 hit.
2. **Grep-filtered compile output lied again** (Phase 13 lesson, twice this phase):
   "clean" runs that were actually failing to find `mvnw` or missing imports. Rule:
   `tail` the real output; verify the new suite actually ran.
3. **JAVA_HOME drift between shells**: machine env points at a removed JDK dir and the
   bash `mvnw` chokes on the backslash path. Fixed with `.freebuff/run-backend.cmd`
   setting `JAVA_HOME=C:\Program Files\Java\jdk-21.0.10` explicitly; test commands use
   the forward-slash inline env.
4. **TS union-type inference**: `params = cond ? {a, size} : {size}` inferred an
   `employeeId?: undefined` property that violates HttpClient's params record type.
   Built the object imperatively with a `Record<string, string>` instead.
5. **Silent kill of a running ng serve**: my second detached launcher failed on the
   occupied port while the first watcher kept serving STALE code (logged error, no
   rebuild). Rule: after contract changes, kill known PIDs first, restart once, verify
   via `netstat` + fresh response payloads.
6. Pre-existing gap surfaced: `MissingServletRequestParameterException` had no handler —
   a missing required query param 500'd. Added 400 handlers for missing and type-
   mismatched params (also improves bad-enum messages to name the parameter).

**Verification:** backend **165/165** (6 new `ListHardeningApiTest` covering envelope,
search/status, clamps, far-page, validation, counts, RBAC — assertions chosen to be
order-tolerant since RecruitmentApiTest runs later), `ng build` clean, **30/30**
frontend tests. Live: envelope + search + clamps + 400s + counts verified over HTTP;
UI verified as admin (recruitment KPIs + tabs + tables, notifications page + badge,
leave page strip) with clean network logs showing `size=100` requests.

**Docs updated:** `API_DOCUMENTATION.md` (Phase 14 list contract + error contract),
`ROADMAP.md` (Phase 14 ✅), `DEVELOPMENT_LOG.md` (this entry).

**Next:** Phase 15 — Reports (CSV/PDF exports).

## PHASE 15 — REPORTS (CSV/PDF EXPORTS)

**Goal:** downloadable CSV and PDF exports for the four main HR lists (employees,
payroll, attendance, leave), honouring each page's current filters, with the same read
role matrix (ADMIN/HR/MANAGER).

**Design:**
- New `com.hrgenius.report` package: `CsvBuilder` (RFC 4180: CRLF, quote-doubling,
  comma escaping; UTF-8 BOM added by the controller for Excel) and `ReportPdfBuilder`
  (OpenPDF — the maintained LGPL/MPL fork of iText 4, added to the pom — A4 landscape,
  title + timestamp + grey header row).
- `ReportService` re-uses each module's own detail queries (`EmployeeSpecifications`,
  `findForPeriodWithEmployee`, `findMonthWithDetails`, leave `findAllWithDetails`) so
  exports always match what the UI shows; it only projects rows to strings.
- `ReportController`: 8 endpoints under `/api/v1/reports` returning raw bytes with
  `Content-Disposition: attachment` — deliberately outside the ApiResponse envelope so
  browsers save a real file.
- **CSV injection defence (OWASP):** any cell whose first character is `= + - @` gets a
  `'` prefix, and raw CR/LF are flattened — a phone `+91-…` or a reason `=HYPERLINK…`
  can never execute as a formula when opened in Excel/Sheets. Visible in output:
  phones export as `'+91-9810011122`.
- Frontend: shared `ReportService` (blob → object URL → anchor click → revoke, with a
  snackbar on failure). Export CSV/PDF buttons on Employees (search + status), Payroll
  (selected period), Attendance (viewed month range) and Leave (status tab).

**Bugs the cycle caught** (ERROR → ROOT CAUSE → FIX → VERIFY)
1. **Wrong enum nesting**: `Employee.EmployeeStatus` — the status enums are top-level
   classes (`EmployeeStatus`), not nested. Compile fixed by importing directly.
2. **BOM in the first CSV field**: the test compared the header line to the bare string,
   but the BOM is part of `lines[0]` — asserted the BOM'd header instead.
3. **PDF text is compressed**: OpenPDF Flate-decodes content streams, so grepping raw
   bytes for "Employee Directory" fails. Tests assert structure (`%PDF-` magic, `%%EOF`)
   instead — text presence belongs to a rendering test with a parser.
4. **Order-tolerance again**: EmployeeApiTest creates its **own** EMP900 (my "seed-only
   employees" assumption was wrong, caught only because the failure dumped the CSV),
   onboarding creates EMP008, LeaveApiTest adds ~9 requests. Exports tests assert seed
   *names* and floors, never totals or code prefixes.
5. **Orphaned ng serve holding a dead log handle**: after a session restart the old
   watcher served stale code and its log file could not even be replaced
   ("Device or resource busy"), making new launches fail confusingly with "port in
   use". Fix: kill node PIDs explicitly, delete the log, relaunch — then verify the
   log mtime and a fresh payload, not just the port.

**Verification:** backend **173/173** (8 new `ReportsApiTest`: CSV header/rows/encoding,
search + status filters, PDF structure, payroll period + empty period, attendance
window chosen to exclude the attendance suite's own writes, leave status filter,
invalid-range 400, unknown-status 400, full read RBAC matrix), `ng build` clean,
**30/30** frontend tests. Live: CSV rows/BOM/sanitisation, `%PDF-` magic, headers,
payroll/attendance/leave payloads, EMPLOYEE 403, bad-range 400; UI: buttons on all four
pages, employees CSV download observed over the network as 200.

**Docs updated:** `API_DOCUMENTATION.md` (8-endpoint report contract + CSV/PDF rules),
`ROADMAP.md` (Phase 15 ✅), `DEVELOPMENT_LOG.md` (this entry).

**Next:** Phase 16 — optional AI module (resume skill extraction, match score), or
harden what exists (real SQL paging for the slice-paged lists).

## PHASE 16 — AI INSIGHTS (SKILL EXTRACTION & MATCH SCORING)

**Goal:** resume skill extraction and job↔candidate match scoring — implemented as a
**deterministic local engine** (the workspace is self-contained by design, like
Oracle-on-H2): curated skill dictionary + word-boundary matching + a transparent
score, behind a service boundary a real LLM provider could replace later.

**Design:**
- `ai.SkillDictionary`: ~28 canonical skills with lowercase aliases, stable order.
- `ai.SkillExtractor`: case-insensitive, word-boundary-safe matching
  (`(?<![a-z0-9+#])alias(?![a-z0-9+#])`), plus **span-based suppression** — an alias
  match is dropped only when its span lies strictly inside a longer match (`sql` inside
  `oracle sql`). PDF text via OpenPDF `PdfTextExtractor` (instance API, per-page
  getTextFromPage); .txt/.md read as UTF-8; anything else → 400.
- `ai.AiService.matchForJob`: required = extract(title + description); per candidate
  score = floor(100 × matched/required), ties by fewer missing then name; candidates
  without extractable skills are omitted; matched/missing echoed for auditability.
- `ai.AiController`: POST `/ai/resume-skills` (pasted text), POST `/ai/resume-files`
  (multipart, stateless — nothing persisted), GET `/ai/job-matches/{jobId}`;
  ADMIN/HR/MANAGER (the recruitment read matrix).
- Frontend: `ai.service.ts` + `JobMatchDialog` (required-skill chips, per-candidate
  score bars, matched ✓ / missing ✗ chips) with an ✨ Insights button on every Jobs row
  (visible to MANAGER too).

**Bugs the cycle caught** (ERROR → ROOT CAUSE → FIX → VERIFY)
1. **OpenPDF 1.3.43 API drift**: no `SimpleTextExtractionStrategy` in the parser
   package, and `getTextFromPage(reader, page)` is (reader, int) mismatched — the real
   API is `new PdfTextExtractor(reader).getTextFromPage(page)`. Discovered by listing
   the jar contents when javap was unavailable.
2. **Substring prune killed Java**: my first specificity rule dropped any skill whose
   canonical name was a substring of another — so "JavaScript".contains("java")
   deleted **Java** from every resume that also mentioned JavaScript. Replaced with
   span-based suppression (drop a match only when its text span sits inside a longer
   match). The failing test payload made the bug obvious.
3. **Expectation drift on seed text**: I asserted "Java" as required for job 1, but its
   text never says Java — required is exactly {Spring Boot, Microservices, Oracle SQL}
   and Kavya scores 66, not 75. Rule: score only what the text actually says.
4. **Stale ng serve served stale UI**: hot reload did not pick up new files (and a
   missing `MatProgressSpinnerModule` import only surfaced in the watcher log, not in
   `ng build`). Touch + explicit reload; and read `.freebuff/*-run.log` for the truth.

**Verification:** backend **181/181** (8 new `AiApiTest`: exact seed-derived match
payloads for both jobs, pasted-text extraction with boundary cases, a real in-memory
PDF upload generated via the Phase 15 builder, unsupported-type 400, 404, extractor
unit checks, full RBAC), `ng build` clean, **30/30** frontend tests. Live: job 1 →
Kavya 66% with matched/missing lists, job 2 → Fatima 100%, resume-skills → 5 skills
in dictionary order, 404/403 verified; UI: Insights button opens the dialog with
required chips, score bar and 15 skill chips, console clean.

**Docs updated:** `API_DOCUMENTATION.md` (AI endpoint contract + engine rules),
`ROADMAP.md` (Phase 16 ✅ — roadmap complete), `DEVELOPMENT_LOG.md` (this entry).

## FULL REGRESSION PASS (post-Phase 16)

**Scope:** every backend suite, frontend build + tests, and a UI click-through of all
12 pages × 4 roles over the live stack.

**Suites:** backend **181/181**, `ng build` clean, frontend **30/30** — re-verified at
the end after UI fixes.

**ADMIN:** all 12 pages render with live data — dashboard KPIs, employees (7 rows,
paginator "1–7 of 7", export buttons), departments (4), recruitment (2/4/4/2 tabs +
Insights dialog: Kavya 66% with matched/missing chips), onboarding (Rohan IN_PROGRESS),
attendance (today strip), payroll (August · 7 payslips · ₹4,06,000), performance (2
reviews, avg 4/5), documents, notifications (seed row + badge), analytics (8 cards),
leave (KPI strip).

**HR:** dashboard + analytics + payroll run/export OK; recruitment write buttons visible
and the Insights dialog works; **write-flow smoke test through the UI**: created a leave
request for Anita via the dialog (employee → type → dates → reason → submit), the row
appeared, the employee's notification badge went 0 → 1, mark-read hid the badge, and the
request was then cancelled via the API (server side verified by search on reason).

**MANAGER:** dashboard shows its ADMIN/HR-restricted state; employees + leave +
recruitment lists render; write buttons correctly hidden (no Add employee / New type /
New request / Post job / Upload); Insights visible (read matrix).

**EMPLOYEE:** notifications (personal feed, event delivery from the HR smoke test),
dashboard/analytics/attendance/recruitment/employees/documents show restricted or
"Access Denied" states — correct per the RBAC matrix.

**Bugs found and fixed**
1. **Misleading offline claim on 403**: the employees page treated every load error as
   "The backend didn't respond. Check that the API server is running." — shown to
   EMPLOYEE whose directory access is deliberately 403. Added a `forbidden` state
   (lock icon + "Directory is restricted", mirroring the dashboard's pattern).
2. **Swallowed error detail**: leave and performance pages hardcoded "Could not load…"
   instead of surfacing the server's message (attendance/recruitment/documents already
   did). Now both show the backend message (e.g. "Access Denied"), consistent with the
   rest of the app.

Not-a-bug observations: empty type chips on Documents are correct (in-memory H2 restarts
empty; on-disk files are orphans from a previous session — documents rows do not
survive, by design). EMPLOYEE leave-page KPI strip 403s by matrix (summary is
ADMIN/HR); page handles it with the table's restricted state.

**Result:** regression pass complete. All suites green before and after the two UI
fixes; both servers left running (backend :8080, frontend :4200).

## PHASE 17 — SQL PAGING HARDENING (DB-SIDE FILTERS)

Goal: the Phase 14 slice-paged endpoints that face unbounded growth — notifications
and all four recruitment lists, plus leave (the fastest-growing table, many requests
per employee per year) — move to real SQL paging so scale becomes a database concern
instead of a JVM-heap one. Bounded lists (onboarding, performance, documents,
departments/designations admin views) deliberately keep slice paging.

**Backend:**
- New `common.SqlPaging`: `likeEscape` (lower-case + `\\` `%` `_` escaping for
  `LIKE :pattern ESCAPE '\'`) and the `Page → PageResponse` mapping.
- **Notifications**: one native paged query (`SELECT * … WHERE user_id = :userId AND
  (:unreadFlag IS NULL OR read_flag = :unreadFlag) AND (:pattern IS NULL OR …) ORDER BY
  id DESC`) with a filter-exact count twin. `unread` binds as Integer `0` — never a
  Boolean — because READ_FLAG is `NUMBER(1)` and the Phase 12 lesson is that Oracle-mode
  rejects Boolean/NUMBER comparisons. First countQuery draft had a real Java bug: `'\'`
  is an escape sequence compiling to `'`, silently corrupting the SQL — caught before
  it ever ran.
- **Recruitment**: all four lists now paged JPQL projections with explicit count
  queries and typed binds. Jobs carry the per-job application count as a subselect and
  order by `lower(title)`; candidates newest-first (`createdAt desc, id desc`) with
  application counts via a LEFT JOIN; applications/interviews id-ascending. NULL-vacuum
  fixes: nullable search fields (skills, remarks, interviewer name) wrap in `coalesce`
  or a NULL LIKE excludes the row entirely even when another field matched.
- **Leave**: `findPagedWithDetails` keeps the `join fetch` detail joins in the page
  query (lazy-safe DTO mapping) with a separate count twin without fetch joins — Spring
  cannot derive a correct count through fetch joins; the old `findAllWithDetails` stays
  for the Phase 15 report exports.
- New `zscale.ScalePagingApiTest` (4 tests) — package sorted to run LAST alphabetically
  so it cannot disturb earlier suites' exact counts. Proves: honest totals at `size=1`
  (impossible under slice paging — the clamp would cap them), 35-row filtered sets
  counted exactly in SQL, delta math on candidates/applications/leave, DB-side status
  composition, far-out pages (empty content, real total), literal `%` search matching
  nothing (escaping), and the unread filter + mark-all integration through the real
  event pipeline (submit + approve leave → exactly 2 notification events).

**Frontend:** `NotificationService.list()` (fetch-100 + unwrap) replaced by
`listPage(page, size, unreadOnly)`; the notifications page gained a true paginator
(10/20/50), an All/Unread toggle driving the DB-side filter, an unread-aware empty
state, and the badge now reads `/unread` after every page action instead of counting
the visible page's rows (a single page can never speak for the whole feed).

**Bugs caught along the way:** positional-`@Query` mixed with `countQuery =` fails
compilation (`annotation values must be of the form name=value` — five repositories);
my unread flag bound `1` (read) instead of `0` (unread) — inverted semantics caught by
the mark-all test; a `Comparator` import deleted along with the old slice code; test
URLs containing `%20` re-encoded to `%2520` by TestRestTemplate (fixed with a
pre-built `URI`); a leave scale loop that walked off March (day 32) and would have
blown the 12-day CASUAL balance (redistributed across types/months within limits);
two wrong test expectations (candidate marker spacing, APPLIED-filtered baseline).

**Verification:** backend **185/185** (181 + 4 new), `ng build` clean, **30/30**
frontend tests. Live: clamp honesty (`size=1` → full total), DB-side search (`rao` →
Kavya Rao), SQL unread filter (`unread=true` → 200 with empty slice after mark-all),
LIKE-escape (`search=%` → 0), leave status+search compose. UI: notifications
All/Unread toggle, DB-filtered empty state, paginator, mark-read re-render; recruitment
tabs with correct counts and newest-first candidates; console clean. Docs updated
(API contract + Phase 17 section, ROADMAP ✅, this entry).

## Phase 18 — HR-Facing Audit Log (2026-09-14)

**Goal:** who-did-what across every HR workflow, HR/ADMIN only, DB-side from day one.

**Design:** followed the notification-pipeline pattern — each service records its
own audit rows via `AuditService.record(...)` inside the caller's transaction, so
an entry commits exactly when the action commits. `record()` is failure-neutral
(never throws, never alters the caller's result). Actor is denormalized
(id/email/name/role at write time) so the trail survives user changes without
joins. The AI module is read-only and emits nothing; login/logout are excluded
on purpose (auth lifecycle, not HR workflow state).

**Built:** Flyway V5 (`AUDIT_LOG` + 4 indexes: created/action/entity/actor),
`com.hrgenius.audit` package (entity, DTO, repository with paged query + exact
count twin, `AuditActions` catalog of 44 actions, service, controller with
`/audit` + `/audit/entity-types`), wiring into all 10 state-changing services,
frontend `audit` feature (service, timeline page with search/entity/action/date
filters + paginator + loading/forbidden/error/empty states, route, nav item).

**Bugs caught:** JPQL `ESCAPE '\'` is two chars — "Escape character literals must
have exactly a single character" (native SQL wants `'\'`, JPQL wants `'\'`);
5-test context failure because AttendanceApiTest legitimately generates audit rows
before my suite runs (fixed with per-action baselines instead of a grand total);
onboarding's `createRecord` returns a DTO, not an entity (wrong `getId()`); my own
test expected the marker inside audit details when the audited fields never
contain it; search covered actor email but not actor name — "Priya" found nothing
in live UI verification (most natural HR search!); matDatepicker emits raw strings
while typing → `date.getFullYear is not a function` in the console (defensive
`iso()` that forwards only complete valid dates, plus M/D/YYYY parsing).

**Verification:** backend **190/190** (185 + 5 new audit tests: RBAC matrix,
recording with actor attribution + newest-first + details, filters/search/escape/
date-range/AND-composition, honest SQL paging with stable exact totals across
pages and far-out pages, facet endpoint), `ng build` clean, **40/40** frontend
tests (10 new). Live: 401/403/403/200/200 RBAC, HR create → update → admin delete
all attributed correctly, facets, inclusive date bounds, literal `%` → 0, typed
dates filter via the UI, calendar picker works. Preview: timeline renders with
kind-colored icons, actor + role chips, "1 – 2 of 2", empty state with tailored
message, console clean.

## Phase 19 — Audit CSV Export (2026-09-25)

**Goal:** download the Phase 18 trail as CSV, respecting the filters currently
applied in the UI, reusing the Phase 15 export infrastructure.

**Design decisions:**

- Reused `CsvBuilder` (RFC 4180 + formula-injection defence), the BOM +
  `Content-Disposition` conventions and the raw-byte response style verbatim;
  but the endpoint lives in a new `AuditExportController` under `/api/v1/audit`,
  NOT under `/reports` — ReportController's guard is ADMIN/HR/MANAGER and
  folding audit data into it would either widen that guard or need a special
  case. A separate controller keeps the stricter audit policy honest
  (manager export → 403, proven live and in tests).
- The row cap rides along as a SQL LIMIT (unpaged `@Query` + a `PageRequest.of(0,
  limit)` Pageable → `fetch first ? rows only` in the SQL log), so the database
  — not the JVM — enforces it; default and ceiling 10 000, `limit<=0` → default.
- Export filters share one param-mapper with the list endpoint on BOTH sides
  (backend normalization duplicated into `forExport`, frontend `filterParams`
  with paging omitted), so feed and export cannot drift apart.
- Frontend reuses the shared `ReportService` blob downloader; the button sits
  next to Refresh with a "current filters" tooltip.

**Bugs caught:** a JPQL `ESCAPE '\\'` edit was backslashed wrong when first
pasted; my str_replace tooling twice left placeholder text in method bodies and
the BOM literal once lost its escape and ate a newline (all caught by re-read
and fixed before compile). A test compared a `HttpStatus` enum against `.value()`
ints (compiles fine, fails fast). A stale ng-serve overlay claimed
`entityTypes` had vanished from AuditService after the surgery — the file was
correct and `ng build` green; a dev-server restart cleared it.

**Unrelated but blocking:** the full-suite run exposed a pre-existing calendar
time-bomb in `AnalyticsApiTest` — `avgTenureYears` was asserted as a hardcoded
`3.6` computed from static seed joining dates, so it drifted to `3.7` as the
calendar advanced (reproduced in isolation, zero relation to the export).
Fixed by deriving the expectation from the seed dates (V2: six fixed dates +
EMP007's `CURRENT_DATE - 10`) with the same months-based semantics as the
service, so the test tracks the calendar instead of fighting it.

**Verification:** backend **192/192** (7 audit tests incl. 2 new export tests:
RBAC matrix incl. manager-403/anonymous-401, header + BOM + disposition,
row-for-row parity with the JSON feed and newest-first top row, action/search/
`%`-escape filter parity, SQL-side `limit=2` returning the two newest rows,
`limit=0`/oversized-limit clamping, malformed date → 400), frontend **43/43**
(3 new: export URL from shared mapper without paging, bare-URL case, component
forwards live filter state and hands the URL to the downloader), `ng build`
clean. Live: 200 `text/csv;charset=UTF-8` with attachment disposition, feed/CSV
count parity, action filter, no-match → 0, `search=Smoke` → 2, `limit=1` → 1,
manager 403, anonymous 401; UI button fires `GET /audit/export.csv → 200` and
the blob download completes; console clean.

## Phase 20 — Backend Reliability & Business-Rule Audit (2026-09-26)

Audit phase, not a feature phase. Every service, controller, repository, DTO and
migration (V1–V5) was re-read end to end; the reconnaissance found the existing
protections largely sound (employee UK code/email, self-manager block,
soft-delete + managed-employees guard, designation–department consistency,
guarded deletes, leave overlap/balance/approval-recheck, application TRANSITIONS
map + unique candidate/job pair, interview stage/date/single-live rules,
SELECTED-only onboarding + unique onboarding pairs, UK_PAY_EMP_PERIOD + PAID
immutability, rating 1–5 service+DB, path-traversal-proof documents,
principal-scoped notifications, LIKE-escape, SQL paging, CSV formula-injection
defence, and all ten services recording audit entries in the caller's
transaction, failure-neutral). Five objectively incorrect or missing rules were
fixed; everything else is reported as verified-no-change.

**Issue 1 — Employee manager cycles were reachable.** EmployeeService.update
blocked self-management but allowed A→B→A cycles, e.g. making employee 1's
manager an employee who already reported to employee 1. ROOT CAUSE: only the
one-hop case was checked. FIX: `wouldCreateManagementCycle` walks the manager
chain upward from the candidate manager with a visited set before accepting the
new managerId (create needs no walk — a new employee has no reports yet).
TEST: `updateCannotCreateAManagerReportingCycle` (409, employee untouched,
self-management 409 retained). VERIFY: live HTTP PUT /employees/1 with
managerId=3 → 409 "management reporting cycle".

**Issue 2 — Jobs could carry a past closing date while still active.**
ROOT CAUSE: closingDate was never validated against today. FIX:
`requireSaneClosingDate` rejects a closing date before today for OPEN/DRAFT
jobs; CLOSED is exempt because past dates there are historical record. TEST:
`activeJobWithPastClosingDateIs400` (create 400, update 400, CLOSED d−30 → 201,
self-cleaned). VERIFY: live POST /jobs with closingDate 2020-01-01 → 400.

**Issue 3 — Leave summary approval rate mixed time windows.** ROOT CAUSE:
`approvedThisYear` was counted inside the current-year window but `decided`
(all-time) was the denominator, so in any later year the rate drifted toward a
meaningless value. FIX: `LeaveRequestRepository.countByStatusInBetweenStartDates`
counts decided requests within the same year window as approved. TEST:
`summaryCounts` hardened to derive both numbers and the rate from seed-date
year membership. VERIFY: live GET /leave/summary → pendingCount 2,
approvedThisYear 1, rejectedCount 1, approvalRate 50.0.

**Issue 4 — Attendance could be marked for future dates.** ROOT CAUSE: mark()
accepted any date, so pre-marked PRESENT rows would pollute month views and
later payroll runs. FIX: reject `date.isAfter(LocalDate.now())` with 400
"Attendance cannot be marked for a future date". TEST:
`markFutureDateIs400` (400, then /attendance/today still has no Vikram Singh —
employee 3 has no record today). VERIFY: live POST /attendance/mark with date
2030-01-01 → 400 with that exact message.

**Issue 5 — One review per employee per period existed only in the service.**
ROOT CAUSE: no DB constraint, so any raw insert (manual fix-up script, future
code path) could create duplicates. FIX: V6 migration adds
`UK_PR_EMP_PERIOD (EMPLOYEE_ID, REVIEW_PERIOD)`; NULL periods exempt (Oracle/H2
ignore NULLs in unique keys), seed rows distinct. TEST:
`databaseConstraintBlocksDuplicateEmployeePeriod` performs the raw duplicate
insert via JdbcTemplate and asserts DataIntegrityViolationException naming
UK_PR_EMP_PERIOD; `duplicatePeriodIs409` still guards the API path. VERIFY:
live POST /performance/reviews duplicate → 409 with the service message; full
suite green proves V6 applies cleanly to seed data.

**Test suite hardening (calendar time-bombs).** Tests that hard-coded dates or
month numbers would silently break as the calendar advances, so expectations
were derived from the seed data instead: LeaveApiTest balances/summary (year
membership of the d−20/d−40 seed rows), AnalyticsApiTest leave-demand and
payroll-trend (seed period = previous month), AttendanceApiTest month-view
(month number derived). No behavioral change — same assertions, computed.

**Verification:** backend **196/196** (192 baseline + 4 new regression tests:
manager cycle 409, past closing date 400, future attendance 400, UK_PR_EMP_PERIOD
raw-insert rejection), frontend **43/43**, `ng build` clean. Live HTTP after
restart: health UP (app+db), swagger-ui 200, manager cycle 409, future
attendance 400, past closing date 400, leave summary correct, duplicate review
409. No commits made.

**Known limitations:** Oracle compatibility of V6 verified against H2
MODE=Oracle only — no Oracle instance on this machine; syntax is plain
`ALTER TABLE ... ADD CONSTRAINT` which Oracle supports unchanged. Manager-cycle
walk is O(depth) per update — fine for realistic org depths. Live duplicate
insert tested in-suite against H2 (constraint name check is H2-flavoured
wording inside the violation message).

## Phase 21 — Concurrency & Double-Effect Hardening (2026-09-26)

Follow-up to the Phase 20 audit's recommendation: serial correctness was
constraint-backed, but nothing proved the guards under real parallel requests.
Every transition and duplicate-sensitive flow was re-read; fixes are limited to
objectively missing serialization — no endpoint contract changed, no RBAC
touched.

**Issue 1 — status transitions were racy read-modify-write.** Leave decide/
cancel, payroll PROCESSED/PAID and review rate/acknowledge loaded an entity,
checked the status in Java, and saved; two concurrent decisions could both read
PENDING and both write. ROOT CAUSE: no row-level guard. FIX: V7 migration adds
VERSION columns and the three entities gain JPA @Version — the losing
transaction fails with ObjectOptimisticLockingFailureException, mapped to a
clean 409 by the global handler. TEST: parallel approve+reject storm — exactly
one 200, the rest 409, final status single-valued, balances show exactly one
decision (1.0 used if approved, 0.0 if rejected). VERIFY: suite + live.

**Issue 2 — concurrent identical leave submissions could all pass the overlap
check.** The overlap/balance guards are read-only SELECTs; four identical
submissions fired together could all see "no overlap" and all insert. ROOT
CAUSE: check-then-insert without serialization. FIX: LeaveService.create now
fetches the employee via findByIdForUpdate (SELECT … FOR UPDATE, held to
commit), serializing all per-employee submissions; portable Oracle/H2, no
schema change needed. TEST: 5-way identical submission storm → exactly 1×201,
4×409 (deterministic: losers observe the committed row). VERIFY: live storm.

**Issue 3 — attendance find-then-upsert could race into duplicates.** Two
concurrent marks for the same employee/day could both find nothing and both
insert; only the unique index would fire (as a raw DIJ). FIX: mark() now takes
the same per-employee lock before the find-then-upsert; requests serialize and
UK_ATT_EMP_DATE remains the backstop. TEST: 5-way identical storm → 5×200 upsert
echoes and exactly ONE row in ATTENDANCE (asserted via JdbcTemplate), and a
mixed-status storm ends in exactly one status bucket. VERIFY: live storm —
month view shows a single day key.

**Verified already correct (no change required):** payroll run dedup is
constraint-backed (UK_PAY_EMP_PERIOD; concurrent runs → losers 409, one row per
employee — proven by storm, not assumed); review creation dupes are
UK_PR_EMP_PERIOD-backed (Phase 20); DataIntegrityViolationException already
mapped to 409 (no 500 leak); audit/notification writes need no extra
serialization.

**Test suite (ConcurrencyApiTest, 6 storm tests, alphabetical placement before
leave/payroll):** every created row is cleaned up (payslips deleted, leave
cancelled/deleted, review deleted) or written outside asserted windows
(2099-H1 review, d-9/d-10 attendance, employee 6/7 far-future leave), so all
19 existing suites keep their exact expectations. Suite confirms: PayrollApiTest
relative counts, DashboardApiTest pending=2, LeaveApiTest pending=2, reports
payroll 8-line last-month export, audit baselines delta-based.

**Verification:** backend **202/202** (196 + 6 new storm tests), frontend
**43/43**, ng build clean. Live HTTP after restart with real parallel storms:
duplicate reviews 1×201 + 4×409, identical leave submissions 1×201 + 4×409,
attendance marks 5×200 with a single row; service-level 409s intact; health UP.

**Known limitations:** @Version adds a version read/compare on each guarded
update (negligible). Pessimistic locks are per-employee row locks — cross-
employee flows never contend. Upsert echo semantics (last write wins) retained
deliberately for attendance: parallel HR marks for one employee/day are a
same-business-day correction, not a double effect. Oracle compatibility of V7
syntax verified on H2 MODE=Oracle only.

## Phase 22 — Security, Authorization & RBAC Hardening (2026-09-26)

Security-focused audit of the complete auth surface (SecurityConfig, JWT
filter/service, AuthService, CorsConfig, every controller's @PreAuthorize,
service-level object checks, DTOs, exception handlers, frontend guards/
interceptor) followed by hardening where real gaps existed. No endpoint
contracts changed, no RBAC redesign, no new infrastructure.

**Verified already correct (no change required).** Authorities come from the
DB, not the JWT: the filter re-loads the user each request (ENABLED +
TOKEN_VERSION), so forged role claims cannot escalate and logout/deactivation
is immediate — proven by test with a validly-signed role=ADMIN claim on the
employee user (passes /auth/me, 403 on /admin/only and /payrolls). Login is
enumeration-safe (identical message for unknown email / disabled / wrong
password). Error envelope hides stack traces and parser internals; security
headers (nosniff, frame-deny, no-store) on every response; CORS answers only
the configured origin; CSRF correctly disabled for the stateless design; no
password hashes or tokens in any sampled response; documents path traversal
guard already correct; audit endpoints ADMIN/HR with the export under the same
policy.

**Finding 1 — client request errors surfaced as 500s.** Malformed JSON bodies
and wrong Content-Type on JSON endpoints produced 500 "Unexpected server error"
(HttpMessageNotReadableException / HttpMediaTypeNotSupportedException fell
through to the catch-all handler). Risk: noise in monitoring, misleading
blame, log noise. FIX: dedicated handlers — malformed body → 400 "Malformed
request body", unsupported media type → 415 "Unsupported Content-Type", both in
the standard envelope without internals. TEST: SecurityApiTest.
malformedBodiesAreClientErrorsNot500 (public and authenticated probes; anonymous
requests to protected endpoints correctly stop at 401 before body parsing).
VERIFY: live curl → 400/415.

**Finding 2 — dev H2 console anonymously reachable from any interface.** The
dev profile exposes /h2-console/** with full SQL access; it sat in the
public-paths list unguarded. Risk on a shared network: complete database
control without credentials. FIX: H2ConsoleGuardConfig registers a filter (only
when spring.h2.console.enabled=true, i.e. dev) restricting /h2-console/* to
loopback addresses; non-loopback clients get 404 (console hidden, not
confirmed); the oracle profile disables the console and never registers the
filter. TEST: local 200 in-suite; live non-loopback 404 via the machine's LAN
address. VERIFY: live.

**Test suite (SecurityApiTest, 10 tests).** Anonymous 401 sweep over 28 module
roots (with envelope-shape assertions); JWT failure modes (empty header, empty
bearer, garbage, alg=none unsigned, tampered signature, ghost user, stale
token_version); claim-tampering escalation attempt; RBAC matrix for MANAGER
(16 denied write/sensitive probes + by-design reads incl. payroll views) and
EMPLOYEE (22 denied probes + self-service allows); notification IDOR
(cross-user mark-read → 404, own mailbox usable); malformed-body 400/415
matrix; CORS allow-list; password/token leakage sampling across seven
representative responses; login enumeration parity. Probe design notes: method
security runs after argument resolution, so authenticated write probes carry
valid bodies (otherwise bean validation 400s before the 403); anonymous probes
need none (chain-level 401).

**Verification:** backend **212/212** (202 + 10 new security tests), frontend
**43/43**, ng build clean. Live HTTP after restart: malformed JSON → 400,
wrong Content-Type → 415, H2 console 200 loopback / 404 via LAN IP, anonymous
sweep 401s, manager→audit 403, employee→payrolls 403, employee→own
notifications 200, and the Phase 21 duplicate-review storm still 1×201 +
4×409. No commits made.

**Known limitations:** H2-console guard verified on H2 MODE=Oracle dev only —
the production-oracle profile has no console at all. JWT is HS256 with the
secret env-var-overridable (documented local default) — rotating the secret
and confirming key strength belongs to deployment, not this codebase. No rate
limiting on /auth/login (deliberate: no new infrastructure this phase;
documented as a future option). Export endpoints inherit the module read
policies (manager-visible payroll/report reads are the existing product
design, verified unchanged).

## Phase 23 — Real Oracle Database Verification & Compatibility (2026-09-26)

**REAL ORACLE VERIFICATION: BLOCKED.** Fresh environment probe: no Oracle
Windows services, no ORACLE_HOME/sqlplus/tnsping, no listener on 1521/1522/
1523/2484/51521, no C:/oracle install, no Docker (so no containerized Oracle
Free/XE possible), and the only com.oracle artifact in the local Maven repo is
the JDBC driver. No Oracle instance exists or can be started on this machine.
Per the phase rules the verification was NOT fabricated; everything that can
be legitimately verified without a real server was completed.

**Static compatibility audit (full inventory).** pom.xml carries the Oracle
JDBC driver (ojdbc 23.5) and flyway-database-oracle; application-oracle.yml
switches datasource + OracleDialect via env vars — the profile switch is clean
and secret-safe. Entities map to Oracle-native types: flags are NUMBER(1)
0/1 with CHECK constraints (User.enabled, Notification.read — no native
BOOLEAN anywhere), @Version columns NUMBER(19), money NUMBER(12,2) with
BigDecimal, timestamps TIMESTAMP, dates DATE, descriptions CLOB via @Lob.
Only two native queries exist (notification feed paging + markAllRead) and
both are deliberately Oracle-oriented: 1/0 literals against READ_FLAG, ESCAPE
'\' LIKE, no LIMIT anywhere (paging rides Hibernate's dialect-generated
ROWNUM-style pagination). The health probe uses FROM DUAL. JdbcTemplate is
used only in the health probe and tests. V6 (parenthesized constraint add)
and V1/V5 DDL are Oracle-valid as written.

**Finding — migration syntax would abort the chain on real Oracle.** V3 and
V7 used `ALTER TABLE … ADD COLUMN …`. H2 accepts the COLUMN keyword; real
Oracle rejects it (ORA-01735: invalid ALTER TABLE option — Oracle grammar is
the parenthesized `ADD (column …)`). Impact: on a real Oracle the migration
chain would fail at V3, before the app ever started. FIX: both migrations
rewritten to `ADD (COLUMN TYPE DEFAULT … NOT NULL)` — accepted identically by
H2 Oracle-mode, correct on Oracle. No checksum concerns: verified below by
full chain replay on a brand-new schema (the dev/test DB is always freshly
migrated in-memory).

**Clean-schema verification.** Booted a dedicated backend instance against a
brand-new H2 (MODE=Oracle) database: Flyway created the schema history table,
validated 7 migrations, and applied V1→V7 in order — "Successfully applied 7
migrations … now at version v7" — then Hibernate initialized and the API came
up. Full smoke on the fresh schema: seed intact (7 employees, 2 pending
leaves, dashboard/aggregates correct), payroll period view (7 payslips, net
406,000), leave summary (2/1/1, 50.0%), attendance month roll-ups (7 rows),
CSV export content, audit trail list, attendance write 200, and the Phase 21
duplicate-review storm 1×201 + 4×409 on the fresh database.

**Regression.** Backend H2 suite 212/212 (Flyway validated the edited
migrations on every fresh test context), frontend 43/43, ng build PASS. Dev
backend restored on the standard h2:mem:hrgenius database. No commits made.

**Unverified (requires a real Oracle server — future work).** Oracle
execution of the migration chain; Oracle's ROWNUM pagination plans; LIKE/
case-sensitivity semantics under real NLS settings; OFFSETDateTime↔TIMESTAMP
timezone conversion behavior; pessimistic FOR UPDATE contention under Oracle's
multi-version concurrency; identity restart behavior; real NLS sort/collation.
The spring.profile switch itself (oracle profile) is configured and documented
but has never been executed against a server.

## Phase 24 — Login Rate Limiting & Brute-Force Protection (2026-09-26)

**Threat model.** (A) password brute force against one account, (B) credential
stuffing across accounts, (C) rapid repeated requests, (D) lockout abuse as
denial of service. Chosen posture: per-email tracking (blunts A and slows B),
identical generic responses (removes the enumeration oracle that would enable
A/B to distinguish states), a bounded automatic-expiry window plus immediate
clear on success (caps D — an attacker can at worst delay, never permanently
lock, and the window is short), no per-IP dimension added (single-instance
dev deployment; documented limitation).

**Reconnaissance.** Login flow: AuthController → AuthService.login (BCrypt
match, ENABLED check, TOKEN_VERSION on tokens) → JwtService. Failures already
produced identical envelopes via BadCredentialsException. No attempt tracking
existed. Frontend login component renders `err.error.message` from the
ApiError envelope — a generic lock message required zero frontend changes.

**Chosen mechanism (minimal, architecture-consistent).** In-memory
per-email tracker (`LoginProtectionService`, ConcurrentHashMap keyed by
lowercased email) with `LoginProtectionProperties`
(app.login-protection.enabled / max-failed-attempts=5 / lockout-duration=10m)
bound from application.yml. Deliberately NOT a DB migration: rate-limiting
state is transient protection, not authorization data — a V8 with
FAILED_ATTEMPTS/LOCKED_UNTIL would add schema, seed-interplay and a write per
login for state whose loss (restart) errs toward availability. Zero extra
database queries per login. Counting is atomic via ConcurrentHashMap.compute:
the check-and-record runs inside one keyed computation, so concurrent failed
attempts cannot lose updates and bypass the threshold (Phase 21 discipline
applied at the application layer). Lockout checks run before credential
verification; the threshold-crossing attempt locks; failures while locked do
not extend the window; expiry clears state; success clears state.

**Bug found by the tests and fixed before ship.** The first isLocked()
implementation used computeIfPresent and returned null for entries that were
merely counting (lockedUntil == null) — silently deleting failure counts on
every status read. The serial HTTP test caught it immediately (counts kept
resetting); fixed so only an expired lock clears an entry.

**Tests (LoginProtectionApiTest, 11).** HTTP: valid login still 200; wrong
password generic 401; failures accumulate and the 5th locks; correct
credentials rejected while locked; success resets the counter (proven by
max−1 further failures not locking); unknown vs known email envelopes are
byte-identical modulo timestamp, while locked and while counting; a real 8-way
parallel wrong-password HTTP storm locks the account and the next correct-
credential attempt is 401 (then success-clears restores it). Service-level
with an injectable MutableClock (no sleeps): lock expiry at window end, fresh
counting window after expiry, only the threshold-crossing attempt reports
"triggered", failures during lock absorbed, disabled-config never locks, and a
20-way concurrent recordFailure storm yields exactly one trigger with the
account locked (lost-update proof). @AfterEach clears tracker state so no
other suite inherits a lockout.

**Verification.** Backend 223/223 (212 + 11; AuthApiTest/SecurityApiTest/
SecurityApiTest JWT cases/concurrency suite all green). Live HTTP: 5 failures
→ lock → correct credentials 401 with the identical generic message; restart
clears the lock (recovery works); 3 failures + success + 4 failures → still
200 (reset semantics); manager→audit 403 and garbage token 401 unchanged.
Frontend 43/43, ng build PASS. No passwords, hashes, tokens or authorization
headers logged — only email + outcome, mirroring the existing convention.
Login events remain excluded from the HR audit trail by design. No commits.

**Real Oracle verification remains BLOCKED because no real Oracle instance is
available** (Phase 23 finding unchanged; this phase added no migration, so no
new database verification was required).

**Remaining limitations.** In-memory state: multi-instance deployments would
need a shared store (documented, not built — single-instance deployment);
per-IP throttling and credential-stuffing detection (cross-email velocity) are
not implemented; the 10-minute window applies per email, so an attacker can
re-lock an account repeatedly (bounded DoS, capped by the short window and
automatic expiry).

---

## Phase 24.1 — Frontend logout ordering bug fix (2026-09-27)

**Bug.** `AuthService.logout()` called `clearSession()` (removing the JWT from
localStorage) BEFORE issuing `POST /api/v1/auth/logout`, so the auth
interceptor — which reads the token from localStorage at request time — sent
the request with no `Authorization` header. The authenticated endpoint then
returned 401: `AuthService.logout(email)` never ran, `TOKEN_VERSION` was never
bumped, and the stale token stayed valid server-side until natural expiry.
The 401 also tripped the error interceptor (spurious "session expired" toast +
second redirect).

**Fix.** Frontend only (`core/auth.service.ts`): logout now issues the
authenticated POST while the token is still in storage so the existing
interceptor attaches `Bearer <jwt>`; the local session is cleared and the
user redirected to /login in a `finalize` on the request's completion — on
success OR failure (never trapped in the app); with no stored token the HTTP
call is skipped entirely (clear + redirect only). No backend, interceptor,
schema, API-contract or UI changes.

**Tests.** `auth.service.spec.ts` now wires the real `authInterceptor` and
covers the three required cases: (1) success — Bearer header present while
the request is in flight, session cleared + redirect after; (2) logout API
failure — session still cleared + redirect; (3) no token — no HTTP request
(`expectNone`), still cleared + redirected. Frontend 45/45, ng build PASS.
Backend regression: AuthApiTest 11/11 (incl. `logoutInvalidatesTokenImmediately`)
+ SecurityApiTest 10/10 (incl. anonymous logout → 401). Live HTTP: unauth
logout 401, Bearer logout 200 "Logged out", same token reused afterwards →
401 (version-bump invalidation intact).

---

## Phase 25 — Frontend UI redesign (2026-09-27)

**Scope.** Visual/UX transformation of the Angular frontend onto a centralized
design system. Zero backend, API-contract, auth, or business-logic changes:
every service, guard, interceptor, dialog flow, and server-side pagination
works exactly as before.

**Design system** (`styles.scss`): CSS-variable tokens for surfaces, text,
borders, brand and semantic colors, shadows, radii — light and dark themes
(`color-scheme` + `.dark` overrides; Material 3 reads the same scheme).
Shared classes: buttons, cards, badges (status color language), tables, form
fields, skeletons, state cards, page scaffold, grids. Material widgets re-skinned
to match via M3 system-variable overrides. Inter as the UI font. All 13 page
stylesheets mapped from hardcoded light-only hex/rgba colors to tokens.

**Shell** (`layout/`): custom sidebar with grouped, role-aware navigation
(`navigation.ts` mirrors the backend's @PreAuthorize roles per module; EMPLOYEE
sees only Notifications — matching what the API actually grants), sticky header
with breadcrumb, Ctrl+K command palette (nav filter + live debounced employee
search through the existing /employees endpoint, skipped for EMPLOYEE to avoid
a guaranteed 403), theme toggle with localStorage persistence and no-flash
bootstrap in index.html, notification bell, user menu, mobile off-canvas
sidebar with backdrop. Shared `AvatarComponent` (initials, deterministic hue).

**Public site** (`public/landing.component.*`): premium landing page — hero,
static demo dashboard preview, stats strip, feature grid, role cards, factual
security section, CTA, footer. Routes: `/` public landing, `/login` redesigned
split-screen auth (form logic unchanged), `/app/*` authenticated shell.

**Verification.** Frontend 49/49 (43 prior + theme spec + updated guard target
for /app). ng build PASS (component-style budget raised 4kB→16kB/32kB for the
landing stylesheet; initial budget untouched). Live QA: admin login → shell,
dark mode (persisted), palette (13 nav items, employee search → navigate),
all 13 module pages render non-blank, employee avatars in directory, mobile
390×844 off-canvas open/backdrop/close, EMPLOYEE role sees only permitted nav,
logout clears session → /login. Only console noise is a pre-existing dev-mode
NG0912 dialog-ID collision warning.
