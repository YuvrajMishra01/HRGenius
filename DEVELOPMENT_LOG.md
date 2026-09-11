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
