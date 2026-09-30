@echo off
setlocal EnableExtensions
chcp 65001 >nul
set "TASK_NAME=ZipAI Finance Policy Update"
set "RUNNER=%~dp0run_finance_policy_crawler.bat"

echo Creating weekly finance policy update task...
echo Task  : %TASK_NAME%
echo Time  : Monday 10:00
echo Runner: %RUNNER%

schtasks /Create /TN "%TASK_NAME%" /TR "\"%RUNNER%\"" /SC WEEKLY /D MON /ST 10:00 /F
if errorlevel 1 (
  echo [FAILED] Scheduled task was not created.
  exit /b 1
)

echo [OK] Scheduled task created.
echo The PC must be powered on, the user must be signed in, and ZipAI must be running.
echo Test: schtasks /Run /TN "%TASK_NAME%"
exit /b 0
