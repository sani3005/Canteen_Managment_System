@echo off
setlocal

echo ========================================================
echo   Building Canteen Owner Backend (Pure C + Sockets + MySQL)
echo ========================================================

REM Add 64-bit GCC toolchain to PATH
set "PATH=%~dp0tools\w64devkit\bin;%PATH%"

REM Check MySQL paths
set "MYSQL_INC=C:\Program Files\MySQL\MySQL Server 8.0\include"
set "MYSQL_LIB=C:\Program Files\MySQL\MySQL Server 8.0\lib"

if not exist "%MYSQL_INC%\mysql.h" (
    echo [ERROR] MySQL headers not found at %MYSQL_INC%
    exit /b 1
)

REM Ensure libmysql.dll is present in current directory for execution
if not exist "libmysql.dll" (
    echo [INFO] Copying libmysql.dll to current directory...
    copy "%MYSQL_LIB%\libmysql.dll" . >nul
)

echo [BUILD] Compiling clean C source files...
gcc -O2 -Wall -Wextra -Wno-unused-parameter ^
    -Iinclude ^
    -I"%MYSQL_INC%" ^
    -L"%MYSQL_LIB%" ^
    src\main.c ^
    src\http_server.c ^
    src\db.c ^
    src\auth.c ^
    src\utils.c ^
    src\cJSON.c ^
    src\handlers\canteen_handler.c ^
    src\handlers\menu_handler.c ^
    src\handlers\order_handler.c ^
    src\handlers\otp_handler.c ^
    src\handlers\analytics_handler.c ^
    -lmysql -lws2_32 ^
    -o server.exe

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] server.exe built successfully!
    echo Run with: server.exe
) else (
    echo [FAIL] Compilation failed with error code %ERRORLEVEL%
)

endlocal
