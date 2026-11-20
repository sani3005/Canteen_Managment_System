@echo off
setlocal

echo ========================================================
echo   Building Canteen Owner Backend (Pure C + Sockets + MySQL)
echo ========================================================

REM Find the installed MySQL Connector C headers and libraries.
set "MYSQL_ROOT="
for /d %%D in ("C:\Program Files\MySQL\MySQL Server 8.0" "C:\Program Files\MySQL\MySQL Server 9.4") do (
    if exist "%%~D\include\mysql.h" if exist "%%~D\lib\libmysql.lib" set "MYSQL_ROOT=%%~D"
)
if "%MYSQL_ROOT%"=="" (
    for /d %%D in ("C:\Program Files\MySQL\MySQL Server *") do (
        if exist "%%~D\include\mysql.h" if exist "%%~D\lib\libmysql.lib" set "MYSQL_ROOT=%%~D"
    )
)
if "%MYSQL_ROOT%"=="" (
    echo [ERROR] MySQL headers and libraries were not found under C:\Program Files\MySQL
    exit /b 1
)

set "MYSQL_INC=%MYSQL_ROOT%\include"
set "MYSQL_LIB=%MYSQL_ROOT%\lib"
echo [INFO] Using MySQL from %MYSQL_ROOT%

if not exist "%MYSQL_INC%\mysql.h" (
    echo [ERROR] MySQL headers not found at %MYSQL_INC%
    exit /b 1
)

if not exist "%MYSQL_LIB%\libmysql.dll" (
    echo [ERROR] MySQL runtime library not found at %MYSQL_LIB%\libmysql.dll
    exit /b 1
)
echo [INFO] Copying required MySQL runtime DLLs to current directory...
copy "%MYSQL_LIB%\libmysql.dll" . >nul
for %%F in ("%MYSQL_ROOT%\bin\libssl*.dll" "%MYSQL_ROOT%\bin\libcrypto*.dll") do (
    if exist "%%~fF" copy "%%~fF" . >nul
)

set "VSWHERE=C:\Program Files\Microsoft Visual Studio\Installer\vswhere.exe"
set "VCVARS="
if exist "%VSWHERE%" (
    for /f "usebackq delims=" %%V in (`"%VSWHERE%" -latest -property installationPath`) do (
        if exist "%%V\VC\Auxiliary\Build\vcvars64.bat" set "VCVARS=%%V\VC\Auxiliary\Build\vcvars64.bat"
    )
)
if not exist "%VCVARS%" if exist "C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat" (
    set "VCVARS=C:\Program Files\Microsoft Visual Studio\18\Community\VC\Auxiliary\Build\vcvars64.bat"
)

if exist "%VCVARS%" (
    echo [BUILD] Compiling with Microsoft Visual C^+^+...
    call "%VCVARS%" >nul
    cl /nologo /O2 /W3 /TC /Iinclude /I"%MYSQL_INC%" ^
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
        src\handlers\user_handler.c ^
        /link /LIBPATH:"%MYSQL_LIB%" libmysql.lib ws2_32.lib bcrypt.lib /OUT:server.exe
) else (
    echo [BUILD] Microsoft Visual C^+^+ not found; compiling with GCC...
    set "PATH=C:\Program Files\CodeBlocks\MinGW\bin;%PATH%"
    copy "C:\Program Files\CodeBlocks\MinGW\bin\libgcc_s_seh-1.dll" . >nul 2>&1
    copy "C:\Program Files\CodeBlocks\MinGW\bin\libwinpthread-1.dll" . >nul 2>&1
    gcc -O2 -Wall -Wextra -Wno-unused-parameter ^
        -Iinclude ^
        -I"%MYSQL_INC%" ^
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
        src\handlers\user_handler.c ^
        -L"%MYSQL_LIB%" -lmysql -lws2_32 -lbcrypt ^
        -o server.exe
)

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] server.exe built successfully!
    echo Run with: server.exe
) else (
    echo [FAIL] Compilation failed with error code %ERRORLEVEL%
)

endlocal
