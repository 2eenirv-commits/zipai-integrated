@echo off
setlocal EnableExtensions

set "TASK_NAME=ZipAI Property Listing Import"
set "RUNNER=%~dp0run_property_listing_import.bat"

if not exist "%RUNNER%" (
  echo [ERROR] Runner not found: %RUNNER%
  echo Keep this file in the same rpa folder as run_property_listing_import.bat.
  exit /b 1
)

echo Creating Windows scheduled task...
echo Task  : %TASK_NAME%
echo Times : 11:00 and 15:00 every day
echo Runner: %RUNNER%

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$action=New-ScheduledTaskAction -Execute '%RUNNER%';" ^
  "$triggers=@((New-ScheduledTaskTrigger -Daily -At 11:00);(New-ScheduledTaskTrigger -Daily -At 15:00));" ^
  "$user=[System.Security.Principal.WindowsIdentity]::GetCurrent().Name;" ^
  "$principal=New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited;" ^
  "$settings=New-ScheduledTaskSettingsSet -ExecutionTimeLimit (New-TimeSpan -Hours 2) -MultipleInstances IgnoreNew -StartWhenAvailable;" ^
  "Register-ScheduledTask -TaskName '%TASK_NAME%' -Action $action -Trigger $triggers -Principal $principal -Settings $settings -Force | Out-Null"
if errorlevel 1 (
  echo [ERROR] Failed to create scheduled task.
  echo Right-click this BAT and select "Run as administrator", then try again.
  exit /b 1
)

echo.
echo [OK] Scheduled task created for every day at 11:00 and 15:00.
echo The PC must be powered on, the user must be signed in, and ZipAI on port 8080 must be running.
echo CSV/JSON files must be placed in rpa\data\listing_import\incoming.
echo.
echo Test now:
echo   schtasks /Run /TN "%TASK_NAME%"
echo Remove task:
echo   schtasks /Delete /TN "%TASK_NAME%" /F
echo.
schtasks /Query /TN "%TASK_NAME%" /FO LIST
exit /b %ERRORLEVEL%
