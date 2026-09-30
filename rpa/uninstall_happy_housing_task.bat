@echo off
setlocal
set "TASK_NAME=ZipAI Happy Housing Crawler"
schtasks /Delete /TN "%TASK_NAME%" /F
exit /b %ERRORLEVEL%
