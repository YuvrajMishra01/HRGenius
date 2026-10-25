# HRGenius — Architecture

## 1. System Overview

HRGenius is a **modular monolith**: one Spring Boot application exposing a REST API consumed by a single Angular SPA, backed by a relational database (Oracle; H2 in Oracle-compat mode for local dev).

```
┌──────────────┐        HTTPS/JSON        ┌─────────────────────────────┐
│   Angular    │ ───────────────────────► │        Spring Boot          │
│     SPA      │   /api/v1/* (JWT)        │  Controller → DTO           │
│              │ ◄─────────────────────── │      → Service → Repository │
│ Material UI  │        JSON              │      → JPA/Hibernate        │
└──────────────┘                          │            ↓                │
                                          │      Flyway migrations      │
                                          │            ↓                │
                                          │   Oracle / H2(Oracle mode)  │
                                          └─────────────────────────────┘
```

## 2. Backend Architecture (layered)

```
Controller (REST, /api/v1/**)
    ↓  DTOs only — JPA entities never cross the API boundary
Service (business rules, transactions)
    ↓
Repository (Spring Data JPA)
    ↓
Entity (JPA, mapped tables)
    ↓
Oracle / H2
```

Package layout under `com.hrgenius`:

| Package       | Responsibility |
|---------------|----------------|
| `config`      | CORS, OpenAPI, app-wide beans |
| `security`    | JWT filter, token provider, `UserDetailsService` wiring |
| `auth`        | login, current-user endpoints |
| `common`      | `ApiResponse<T>` envelope, `ApiError`, global exception handler, health controller |
| `employee` / `department` / `recruitment` / `onboarding` / `attendance` / `leave` / `payroll` / `performance` / `documents` / `notification` / `analytics` | one vertical slice per business module — each contains its own controller, service, repository, entities, DTOs |

**Database switching.** The code targets Oracle dialect everywhere. Two Spring profiles:

- `dev` (default): H2 in-memory, `MODE=Oracle` — zero install, same Flyway scripts.
- `oracle`: real Oracle via `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables.

Schema changes happen **only** through Flyway migrations in `backend/src/main/resources/db/migration`.

## 3. Frontend Architecture (feature-based)

```
src/app/
├── core/            HttpClient infra: auth interceptor, error interceptor,
│                    api service wrapper, notification toast service
├── shared/          reusable UI: table, pagination, search box, confirm dialog,
│                    loading indicator, empty state
├── auth/            login page, auth facade service, route guards
├── layout/          shell: sidebar + navbar + content outlet
└── <feature>/       dashboard, employees, departments, recruitment, onboarding,
                     attendance, leave, payroll, performance, documents,
                     notifications, analytics — each lazy-loaded
```

All HTTP calls go through `core/api.service.ts`; the auth interceptor attaches the JWT, the error interceptor normalizes backend `ApiError` responses into toasts.

## 4. Authentication & Authorization Flow

```
Login (email + password)
  → AuthController → BCrypt verify
  → JwtTokenProvider issues signed HS256 token (claims: sub, uid, role, exp)
  → Angular stores token (memory + localStorage)
  → every request: Authorization: Bearer <token>
  → JwtAuthenticationFilter validates signature + expiry, sets SecurityContext
  → @PreAuthorize role checks (ADMIN / HR / MANAGER / EMPLOYEE)
```

Rules:

- Passwords: BCrypt only, never logged, never returned by any API.
- JWT secret and expiry come from environment (`JWT_SECRET`, `JWT_EXPIRATION_MINUTES`) — never hardcoded.
- Authorization is enforced in the backend on every request; Angular guards are UX only.

## 5. API Conventions

- Base path: `/api/v1`
- Envelope: `{ "success": true, "message": "...", "data": {...} }`
- Errors: `{ "timestamp", "status", "message", "path", "errors": {field: msg} }` from a single `@RestControllerAdvice`
- Status codes: 200/201/204, 400 validation, 401 unauthenticated, 403 unauthorized, 404 not found, 409 conflict, 500 unexpected
- Pagination: `?page=0&size=10&sort=createdAt,desc` returning `{ content, page, size, totalElements, totalPages }`

## 6. Module Relationships (business flow)

```
Recruitment (jobs, candidates, applications, interviews)
   → selection creates Employee → Onboarding workflow
Employee → Attendance (daily check-in/out)
Employee → Leave (request → manager approval → balance)
Employee → Payroll (monthly run → payslip)
Employee → Performance (goals, reviews)
All modules → Notifications; aggregates → Analytics/Reports
```

### Business-rule hardening (Phase 20)

Rules are enforced defence-in-depth: a service-layer check produces the friendly
4xx response, and a DB constraint (e.g. `UK_PR_EMP_PERIOD`, V6) remains the last
line of defence against raw inserts. Manager reporting chains are walked acyclic
on update; attendance rejects future dates; active jobs reject past closing
dates; leave summary computes approved/decided inside one year window.

### Concurrency control (Phase 21)

Status-transition flows (leave decide/cancel, payroll PROCESSED/PAID, review
rate/acknowledge) carry a JPA `@Version` optimistic lock (V7): a concurrent
loser fails with `ObjectOptimisticLockingFailureException`, mapped to 409 —
never a silent overwrite, never a 500. Flows whose guard is a read-only check
(leave overlap/balance, attendance find-then-upsert) take a per-employee
`SELECT … FOR UPDATE` pessimistic lock so concurrent requests serialize; unique
constraints (`UK_PAY_EMP_PERIOD`, `UK_PR_EMP_PERIOD`, `UK_ATT_EMP_DATE`) remain
the last line of defence and surface as clean 409s via the global exception
handler. Verified by parallel duplicate-request storm tests.

### Security model (Phase 22)

Stateless JWT with DB-derived authorities: the filter re-loads the user on
every request (checks `ENABLED` + `TOKEN_VERSION`), so role claims in tokens
are never trusted and logout/deactivation is immediate. Method-level
`@PreAuthorize` on every business endpoint; the security chain answers 401
before controllers for anonymous callers. Login is enumeration-safe; the
client-error handlers map malformed bodies (400) and unsupported media types
(415) instead of 500s; the dev-only H2 console is loopback-gated. The security
boundary is proven by `SecurityApiTest`: anonymous 401 sweep across all module
roots, JWT failure modes (garbage/unsigned/tampered/ghost/revoked), RBAC
matrix, claim-tampering escalation attempt, object-level notification IDOR,
CORS origin allow-list, and credential-leakage sampling.

### Login abuse protection (Phase 24)

`POST /auth/login` is guarded by an in-memory per-email failed-attempt tracker
(`LoginProtectionService`): after a configurable number of failures the email
is locked out for a configurable duration, with the identical generic 401 for
locked/unknown/wrong-password attempts (enumeration-safe). Counting is atomic
(`ConcurrentHashMap.compute`) so concurrent attempts cannot lose updates and
slip past the threshold; success resets the count; expiry is clock-based and
fully recovers access. Zero database queries are added to any login. State is
deliberately transient (restart clears it); login events stay out of the HR
audit trail by design.

### Oracle compatibility (Phase 23)

The codebase is Oracle-first by construction: Flyway migrations use
Oracle types (`VARCHAR2`/`NUMBER`/`CLOB`, `GENERATED BY DEFAULT AS IDENTITY`),
flags are `NUMBER(1)` with 0/1 CHECK constraints (no native BOOLEAN), native
SQL avoids `LIMIT` (paging goes through Hibernate's dialect, V6/V7 use
parenthesized `ADD (column)` grammar), the health probe is `FROM DUAL`, and
`spring.profiles.active=oracle` + `DB_URL/DB_USERNAME/DB_PASSWORD` env vars
switch to a real Oracle with no code changes (verified dialect:
`org.hibernate.dialect.OracleDialect`). Verified via clean-schema migration
chain replay (V1→V7) and full API smoke on H2 MODE=Oracle; execution against a
real Oracle server remains future work (none available in the dev
environment).

## 7. Deployment (target)

## 7. Deployment (target)

Single deployable `backend.jar` serving both the API and the built Angular bundle (`frontend/dist` copied into `src/main/resources/static` for production), behind any servlet container. Local development runs the two independently with an Angular dev proxy.
