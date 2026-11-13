@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   Initializing Canteen Database from schema.sql
echo ========================================================

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if not exist "%MYSQL_BIN%" (
    set "MYSQL_BIN=mysql.exe"
)

REM Default settings
set "DB_PORT=3307"
set "DB_USER=root"
set "DB_PASS="

REM Try to read from .env if present
if exist .env (
    for /f "usebackq tokens=1,* delims==" %%A in (.env) do (
        if "%%A"=="DB_PORT" set "DB_PORT=%%B"
        if "%%A"=="DB_USER" set "DB_USER=%%B"
        if "%%A"=="DB_PASS" set "DB_PASS=%%B"
    )
)

if "%DB_PASS%"=="" (
    set /p "DB_PASS=Enter MySQL root password: "
)

echo [INFO] Running schema.sql against MySQL (Port: %DB_PORT%, User: %DB_USER%)...
"%MYSQL_BIN%" -P %DB_PORT% -u %DB_USER% -p%DB_PASS% < schema.sql

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Database 'canteen_db' initialized successfully with seed data!
    echo.
    echo Owner Token: owner_secret_token_123
    echo Canteen ID:  1
    echo.
) else (
    echo.
    echo [ERROR] Failed to execute schema.sql. Please check your MySQL password.
)

endlocal
