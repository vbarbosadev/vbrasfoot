@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0vbrasfoot\app\launch.ps1" %*
set "vbrasfoot_exit=%errorlevel%"
if not "%vbrasfoot_exit%"=="0" pause
exit /b %vbrasfoot_exit%
