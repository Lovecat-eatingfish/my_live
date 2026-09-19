@echo off
powershell -ExecutionPolicy Bypass -File %~dp0ops\reload-single.ps1 %*
pause
