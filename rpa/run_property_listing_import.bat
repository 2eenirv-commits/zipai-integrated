@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
set "PYTHONUTF8=1"
set "PYTHONIOENCODING=utf-8"
cd /d "%~dp0"

if exist "..\.venv\Scripts\python.exe" (
  set "PYTHON=..\.venv\Scripts\python.exe"
) else (
  set "PYTHON=python"
)

if not exist "logs" mkdir "logs"
for /f %%I in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyyMMdd"') do set "RUN_DATE=%%I"
for /f %%I in ('powershell.exe -NoProfile -Command "[DateTimeOffset]::Now.ToUnixTimeSeconds()"') do set "START_SECONDS=%%I"
set "LOG_FILE=%~dp0logs\property_listing_import_!RUN_DATE!.log"
set "LOCK_DIR=%TEMP%\ZipAI_Property_Listing_Import.lock"

if exist "!LOCK_DIR!" (
  powershell.exe -NoProfile -Command "if (((Get-Date)-(Get-Item -LiteralPath '!LOCK_DIR!').LastWriteTime).TotalHours -ge 2) { exit 0 } else { exit 1 }"
  if not errorlevel 1 rmdir "!LOCK_DIR!" 2>nul
)
mkdir "!LOCK_DIR!" 2>nul
if errorlevel 1 (
  echo [%date% %time%] SKIP another listing import is already running.>>"!LOG_FILE!"
  exit /b 20
)

echo.>>"!LOG_FILE!"
echo [%date% %time%] START scheduled property listing import>>"!LOG_FILE!"
"!PYTHON!" property_listing_batch.py >>"!LOG_FILE!" 2>&1
set "EXITCODE=!ERRORLEVEL!"
if "!EXITCODE!"=="0" (
  set "RESULT=OK listing import completed"
) else (
  set "RESULT=ERROR listing import failed"
)

for /f %%I in ('powershell.exe -NoProfile -Command "[DateTimeOffset]::Now.ToUnixTimeSeconds()"') do set "END_SECONDS=%%I"
set /a ELAPSED_SECONDS=END_SECONDS-START_SECONDS
echo [%date% %time%] !RESULT!. exit=!EXITCODE! elapsed=!ELAPSED_SECONDS!s>>"!LOG_FILE!"
rmdir "!LOCK_DIR!" 2>nul
exit /b !EXITCODE!
