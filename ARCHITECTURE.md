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

## 7. Deployment (target)

Single deployable `backend.jar` serving both the API and the built Angular bundle (`frontend/dist` copied into `src/main/resources/static` for production), behind any servlet container. Local development runs the two independently with an Angular dev proxy.
