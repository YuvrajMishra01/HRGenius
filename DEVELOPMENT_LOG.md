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
