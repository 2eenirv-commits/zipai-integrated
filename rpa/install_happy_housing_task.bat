@echo off
setlocal EnableExtensions

set "TASK_NAME=ZipAI Happy Housing Crawler"
set "RUNNER=%~dp0run_happy_housing_crawler.bat"
set "RUN_TIME=%~1"
if "%RUN_TIME%"=="" set "RUN_TIME=09:20"

if not exist "%RUNNER%" (
  echo [ERROR] Runner not found: %RUNNER%
  exit /b 1
)

echo Creating Windows scheduled task...
echo Task : %TASK_NAME%
echo Time : %RUN_TIME%
echo Runner: %RUNNER%

schtasks /Create /TN "%TASK_NAME%" /TR "\"%RUNNER%\"" /SC DAILY /ST %RUN_TIME% /F
if errorlevel 1 (
  echo [ERROR] Failed to create scheduled task.
  echo Try running this BAT as Administrator if Windows blocks task creation.
  exit /b 1
)

echo [OK] Scheduled task created.
echo To run now:
schtasks /Run /TN "%TASK_NAME%"
exit /b 0
