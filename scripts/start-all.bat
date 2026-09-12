@echo off
REM ============================================================
REM  Qiyu Live Platform - one-click start/stop (Windows)
REM  Thin wrapper: real logic lives in start-all.ps1 (same folder)
REM  Usage:
REM    start-all.bat             build (if jar missing) + start all
REM    start-all.bat infra       docker compose middleware + nacos namespace
REM    start-all.bat build       mvn clean install -DskipTests
REM    start-all.bat start       start only
REM    start-all.bat stop        stop all
REM    start-all.bat restart     restart
REM    start-all.bat status      middleware ports + running services
REM    start-all.bat logs qiyu-live-api   tail a module log
REM    start-all.bat web         vite dev server (http://localhost:3000)
REM  Env overrides: QIYU_JAVA_HOME / QIYU_JAVA_OPTS / STARTUP_WAIT / NACOS_ADDR
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
