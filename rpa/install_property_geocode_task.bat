@echo off
setlocal EnableExtensions

set "TASK_NAME=ZipAI Property Geocode"
set "RUNNER=%~dp0run_property_geocode_batch.bat"
set "START_TIME=09:15"
set "LAST_TIME=17:15"

if not exist "%RUNNER%" (
  echo [ERROR] Runner not found: %RUNNER%
  echo Keep this file in the same rpa folder as run_property_geocode_batch.bat.
  exit /b 1
)

echo Creating Windows scheduled task...
echo Task  : %TASK_NAME%
echo Hours : every 1 hour from %START_TIME% through %LAST_TIME%
echo Runner: %RUNNER%

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$action=New-ScheduledTaskAction -Execute '%RUNNER%';" ^
  "$triggers=9..17 | ForEach-Object { New-ScheduledTaskTrigger -Daily -At ([datetime]::Today.AddHours($_).AddMinutes(15)) };" ^
  "$user=[System.Security.Principal.WindowsIdentity]::GetCurrent().Name;" ^
  "$principal=New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited;" ^
  "$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 1) -MultipleInstances IgnoreNew;" ^
  "Register-ScheduledTask -TaskName '%TASK_NAME%' -Action $action -Trigger $triggers -Principal $principal -Settings $settings -Force | Out-Null"
if errorlevel 1 (
  echo [ERROR] Failed to create scheduled task.
  echo Right-click this BAT and select "Run as administrator", then try again.
  exit /b 1
)

echo.
echo [OK] Scheduled task created.
echo It runs every day, every hour from %START_TIME% through %LAST_TIME%.
echo The PC must be powered on, the user must be signed in, and ZipAI on port 8080 must be running.
echo Existing property crawl and geocode runs share one lock, so they cannot overlap.
echo.
echo Test now:
echo   schtasks /Run /TN "%TASK_NAME%"
echo Remove task:
echo   schtasks /Delete /TN "%TASK_NAME%" /F
echo.
schtasks /Query /TN "%TASK_NAME%" /FO LIST
exit /b %ERRORLEVEL%
