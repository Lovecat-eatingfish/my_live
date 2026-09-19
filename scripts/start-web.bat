@echo off
REM ============================================================
REM  启动前端开发服务器 (web_live :3000 + web_admin :3005)
REM  用法:
REM    start-web.bat              启动 + 自动 pnpm install
REM    start-web.bat -SkipInstall 启动但跳过 pnpm install
REM ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\start-web.ps1" %*
pause
