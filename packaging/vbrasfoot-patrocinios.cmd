@echo off
setlocal
cd /d "%~dp0"
call "%~dp0vbrasfoot.cmd" --sponsors-only --probe-career-hooks --pixel-perfect %*
exit /b %errorlevel%
