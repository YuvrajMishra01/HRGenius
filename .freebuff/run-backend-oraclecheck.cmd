@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
cd /d D:\Project\backend
call "D:\Project\backend\mvnw.cmd" spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:oraclecheck;MODE=Oracle;DB_CLOSE_DELAY=-1" > D:\Project\.freebuff\backend-oraclecheck.log 2>&1
