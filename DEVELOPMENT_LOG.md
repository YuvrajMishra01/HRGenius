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
