# HRGenius — Human Resource Management System

A full-stack HRMS built as a modular monolith: **Spring Boot 3 (Java 21) + Oracle + Angular**.
Covers the complete employee lifecycle: Recruitment → Selection → Employee → Onboarding → Attendance → Leave → Payroll → Performance → Analytics.

## Tech Stack

| Layer     | Technology |
|-----------|------------|
| Backend   | Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, Spring Security + JWT, Bean Validation, Flyway, springdoc OpenAPI |
| Database  | Oracle (JDBC `ojdbc11`, Hibernate `org.hibernate.dialect.OracleDialect`, Flyway migrations in Oracle syntax). Local dev runs on **H2 in Oracle compatibility mode** — switching to real Oracle requires only `DB_*` environment variables (see below) |
| Frontend  | Angular 20, TypeScript, Angular Material, RxJS, Angular Router |
| Build     | Maven Wrapper (`mvnw`), npm + Angular CLI |

## Repository Layout

```
HRGenius/
├── backend/     Spring Boot REST API  (base path /api/v1)
├── frontend/    Angular SPA
├── docs/        architecture / database / api
├── ARCHITECTURE.md          system & module design
├── ROADMAP.md               phase tracker (current status)
├── DATABASE_DESIGN.md       ER model, tables, constraints, indexes
├── API_DOCUMENTATION.md     endpoint reference
├── DEVELOPMENT_LOG.md       decisions, bugs, fixes
└── README.md
```

## Prerequisites

- Java 17+ (developed on Java 21)
- Node.js 18+ / npm
- No global Maven/Angular CLI needed (`mvnw`, `npx ng`)

## Database Setup

HRGenius is Oracle-first. Two supported modes:

### Mode A — H2 (default, zero install)

Nothing to do. The backend uses the `dev` profile by default:

- H2 in-memory in Oracle compatibility mode (`MODE=Oracle`)
- Flyway migrations run automatically at startup
- Console at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:hrgenius`)

### Mode B — Real Oracle (XE / 23ai Free / full)

Start Oracle, then run the backend with environment variables:

```
DB_URL      jdbc:oracle:thin:@localhost:1521/XEPDB1
DB_USERNAME hrgenius
DB_PASSWORD <secret>
```

The `oracle` Spring profile (`SPRING_PROFILES_ACTIVE=oracle`) wires the same Flyway scripts against Oracle. No code changes.

Secrets are never committed — `application-local.yml` and `.env` are git-ignored.

## Backend Setup

```bash
cd backend
./mvnw spring-boot:run          # Windows Git Bash; use mvnw.cmd in cmd.exe
```

- API base: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/api/v1/health

```bash
./mvnw test                     # run tests
./mvnw clean verify             # compile + tests + package
```

## Frontend Setup

```bash
cd frontend
npm install
npm start                       # dev server on http://localhost:4200, proxies /api → :8080
npm run build                   # production build
npm test                        # unit tests (karma)
```

## Demo Accounts (development seed)

Created by the seed migration. Passwords are bcrypt-hashed; these values exist **only** for local development.

| Email                      | Role     | Password     |
|----------------------------|----------|--------------|
| admin@hrgenius.local       | ADMIN    | Admin@123    |
| hr@hrgenius.local          | HR       | Hr@12345     |
| manager@hrgenius.local     | MANAGER  | Manager@123  |
| employee@hrgenius.local    | EMPLOYEE | Employee@123 |

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — layered architecture, security flow, module map
- [ROADMAP.md](ROADMAP.md) — phase plan and status
- [DATABASE_DESIGN.md](DATABASE_DESIGN.md) — ER model and schema
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) — REST reference (plus live Swagger)
- [DEVELOPMENT_LOG.md](DEVELOPMENT_LOG.md) — chronological build log
