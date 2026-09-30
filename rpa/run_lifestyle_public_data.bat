@echo off
setlocal
cd /d "%~dp0"

set "PYTHON_EXE=python"
if exist ".venv\Scripts\python.exe" set "PYTHON_EXE=.venv\Scripts\python.exe"

if "%ZIPAI_DB_PASSWORD%"=="" (
  echo [INFO] ZIPAI_DB_PASSWORD environment variable is empty.
  echo [INFO] If your MySQL user has a password, set it before running this file.
)

echo [1/2] Building normalized Lifestyle scores from official raw CSV files...
"%PYTHON_EXE%" lifestyle_public_data_builder.py
if errorlevel 1 goto :error

echo [2/2] Importing normalized scores into MySQL...
"%PYTHON_EXE%" lifestyle_score_importer.py
if errorlevel 1 goto :error

echo [OK] Lifestyle public-data pipeline finished.
exit /b 0

:error
echo [ERROR] Lifestyle public-data pipeline failed. exit=%errorlevel%
exit /b %errorlevel%
