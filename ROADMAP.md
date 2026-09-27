# HRGenius — Roadmap

Status legend: ✅ done · 🔄 in progress · ⬜ pending

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundation: Spring Boot + Angular scaffolds, DB profiles, common API envelope, exception handling, health API, docs | ✅ |
| 1 | Auth: User entity, BCrypt, JWT, Spring Security, Angular login/guards/interceptor | ✅ |
| 2 | Admin dashboard with aggregated stats APIs + charts | ✅ |
| 3 | Employee CRUD, search/filter/pagination | ✅ |
| 4 | Department & designation management | ✅ |
| 5 | Recruitment: jobs, candidates, applications, interviews | ✅ |
| 6 | Onboarding workflow (candidate → employee → completion %) | ✅ |
| 7 | Attendance: check-in/out, monthly views | ✅ |
| 8 | Leave: types, balance, request → manager approval workflow | ✅ |
| 9 | Payroll: salary structure, monthly run, payslip (BigDecimal) | ✅ |
| 10 | Performance: goals, KPIs, manager review, ratings | ✅ |
| 11 | Documents: upload/download/delete, metadata, validation | ✅ |
| 12 | Notifications: events + unread badge | ✅ |
| 13 | Analytics: real-data charts | ✅ |
| 14 | Cross-cutting search/filter/pagination hardening | ✅ |
| 15 | Reports: CSV/PDF exports | ✅ |
| 16 | Optional AI module (resume skill extraction, match score) | ✅ |
| 17 | SQL paging hardening: DB-side filters + real paging for notifications, recruitment, leave | ✅ |
| 18 | HR-facing audit log: who-did-what trail with DB-side filters, ADMIN/HR only | ✅ |
| 19 | Audit CSV export: Phase 15 export machinery, current filters respected, SQL-side row cap, ADMIN/HR only | ✅ |
| 20 | Backend reliability & business-rule audit: manager-cycle guard, job closing-date sanity, leave summary year window, attendance future-date guard, performance-period DB constraint | ✅ |
| 21 | Concurrency & double-effect hardening: optimistic locking on guarded transitions, per-employee pessimistic serialization for leave/attendance, parallel request-storm test suite | ✅ |
| 22 | Security, authorization & RBAC hardening: full boundary test suite (anonymous sweep, JWT failure modes, RBAC matrix, IDOR), client-error 400/415 handling, loopback-only H2 console | ✅ |
| 23 | Oracle compatibility verification: static dialect audit, Oracle-grammar migration fix (V3/V7), clean-schema chain replay, full smoke — real-Oracle execution blocked (no Oracle/Docker on machine) | ✅* |
| 24 | Login rate limiting & brute-force protection: per-email failed-attempt tracking, configurable temporary lockout, atomic counting, generic responses, storm-proven | ✅ |

## Environment Decision Log

- **Database:** machine has no Oracle (no service on 1521, no Docker, no sqlplus). Decision: Oracle-first codebase (Oracle JDBC driver, Oracle Flyway dialect), running on H2 `MODE=Oracle` locally via the `dev` profile. Switching to real Oracle = `SPRING_PROFILES_ACTIVE=oracle` + `DB_URL/DB_USERNAME/DB_PASSWORD` env vars. No code changes needed later.
- **Maven:** not installed globally → using Maven Wrapper committed to the repo.
- **Angular:** no global CLI → Angular CLI as devDependency, invoked via `npm start` / `npx ng`.
- **Java:** 21 LTS available (spec requires 17+; targeting release 21).

## Working Agreement

Per the master prompt: one phase at a time; compile → test → run → verify before moving on; `ERROR → ROOT CAUSE → FIX → TEST → VERIFY` for every failure; phase summaries recorded in `DEVELOPMENT_LOG.md`.
