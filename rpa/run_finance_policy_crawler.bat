@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0\.."

if exist ".env" (
  for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    if not "%%A"=="" if not "%%A:~0,1"=="#" set "%%A=%%B"
  )
)

if not exist "rpa\logs" mkdir "rpa\logs"
set "LOG_FILE=rpa\logs\finance_policy_update.log"
echo.>>"%LOG_FILE%"
echo [%date% %time%] START finance policy update>>"%LOG_FILE%"

if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" "rpa\finance_policy_crawler.py" >>"%LOG_FILE%" 2>&1
) else (
  python "rpa\finance_policy_crawler.py" >>"%LOG_FILE%" 2>&1
)
set "EXIT_CODE=%ERRORLEVEL%"

if "%EXIT_CODE%"=="0" (
  echo [%date% %time%] OK finance policy update completed.>>"%LOG_FILE%"
) else (
  echo [%date% %time%] FAILED finance policy update. exit=%EXIT_CODE%>>"%LOG_FILE%"
)
exit /b %EXIT_CODE%
