@echo off
setlocal
cd /d "%~dp0"
call "%~dp0vbrasfoot.cmd" --combined --probe-career-hooks --pixel-perfect %*
exit /b %errorlevel%
