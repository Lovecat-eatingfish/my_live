@echo off
REM ============================================================
REM  Qiyu Live Platform - 按依赖顺序逐个启动服务
REM  用法:
REM    start-step-all.bat                启动 + 检查中间件
REM    start-step-all.bat -SkipInfra     跳过中间件检查
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\start-step-all.ps1" %*
pause
