@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_happy_housing_crawler.ps1"
exit /b %ERRORLEVEL%
