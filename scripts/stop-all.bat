@echo off
REM ============================================================
REM  Qiyu Live Platform - one-click stop ALL Java services
REM  (middleware containers & srs.exe are NOT touched)
REM  Usage:
REM    stop-all.bat
REM  Logic: jps matches qiyu JVMs (precise) + service-port sweep (fallback)
REM  See stop-all.ps1 for details; port list synced with docs/ports.md
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1" %*
