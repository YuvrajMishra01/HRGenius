@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
cd /d D:\Project\backend
call "D:\Project\backend\mvnw.cmd" spring-boot:run > D:\Project\.freebuff\backend-run.log 2>&1
