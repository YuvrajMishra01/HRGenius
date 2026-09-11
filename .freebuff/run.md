# HRGenius — Local Run Guide

## 1. Reproduce build artifacts

### Backend jar (skip if `backend/target/hrgenius-backend-0.1.0-SNAPSHOT.jar` exists)
```bash
cd backend
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.10"   # Git Bash; Windows: set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
./mvnw -q package -DskipTests
```

### Frontend dependencies (skip if `frontend/node_modules` exists)
```bash
cd frontend && npm install
```

## 2. Run servers (detached, survives terminal close)

Backend — port 8080:
```powershell
powershell -NoProfile -Command "(Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList '-jar','D:\Project\backend\target\hrgenius-backend-0.1.0-SNAPSHOT.jar' -WorkingDirectory 'D:\Project\backend' -RedirectStandardOutput 'D:\Project\.freebuff\backend.log' -RedirectStandardError 'D:\Project\.freebuff\backend.log.err' -WindowStyle Hidden -PassThru).Id"
```

Frontend — port 4200 (`npm start` = `ng serve`, uses `frontend/proxy.conf.json`):
```powershell
powershell -NoProfile -Command "(Start-Process -FilePath 'npm.cmd' -ArgumentList 'start' -WorkingDirectory 'D:\Project\frontend' -RedirectStandardOutput 'D:\Project\.freebuff\frontend.log' -RedirectStandardError 'D:\Project\.freebuff\frontend.log.err' -WindowStyle Hidden -PassThru).Id"
```

## 3. Verify
- Backend: `curl http://localhost:8080/api/v1/health` → `{"success":true,"data":{"status":"UP","database":"UP",...}}`
- Frontend: http://localhost:4200 → dashboard with API/DATABASE status cards.
- The Angular dev server proxies `/api` to `http://localhost:8080` (see `frontend/proxy.conf.json`).

## Notes
- Database: H2 in Oracle-compatibility mode (in-memory, per-boot). Flyway migrations `V1__init_schema.sql`, `V2__seed_data.sql` apply automatically at startup.
- Real Oracle: run with `SPRING_PROFILES_ACTIVE=oracle` plus `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` environment variables.
- Swagger UI: http://localhost:8080/swagger-ui.html · H2 console: http://localhost:8080/h2-console
