@echo off
setlocal EnableExtensions

set "TASK_NAME=ZipAI Property Sale Crawler"
set "RUNNER=%~dp0run_property_sale_crawler.bat"
set "RUN_TIME=09:00"

if not exist "%RUNNER%" (
  echo [ERROR] Runner not found: %RUNNER%
  echo Keep this file in the same rpa folder as run_property_sale_crawler.bat.
  exit /b 1
)

echo Creating Windows scheduled task...
echo Task  : %TASK_NAME%
echo Time  : %RUN_TIME%
echo Runner: %RUNNER%

schtasks /Create /TN "%TASK_NAME%" /TR "\"%RUNNER%\"" /SC DAILY /ST %RUN_TIME% /F
if errorlevel 1 (
  echo [ERROR] Failed to create scheduled task.
  echo Right-click this BAT and select "Run as administrator", then try again.
  exit /b 1
)

echo.
echo [OK] Scheduled task created for every day at %RUN_TIME%.
echo The PC must be powered on, the user must be signed in, and ZipAI on port 8080 must be running.
echo.
echo Test now:
echo   schtasks /Run /TN "%TASK_NAME%"
echo Remove task:
echo   schtasks /Delete /TN "%TASK_NAME%" /F
echo.
schtasks /Query /TN "%TASK_NAME%" /FO LIST
exit /b %ERRORLEVEL%
