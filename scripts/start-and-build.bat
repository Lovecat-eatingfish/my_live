@echo off
REM ============================================================
REM  Qiyu Live Platform - 全量编译并启动服务 (Windows)
REM  调用: scripts\start-and-build.ps1
REM  用法:
REM    start-and-build.bat              编译全部 + 检查中间件 + 按波次启动
REM    start-and-build.bat -SkipInfra   跳过中间件检查，直接编译并启动
REM    start-and-build.bat -StartupWait 5  指定波次间隔秒数
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\start-and-build.ps1" %*
pause
