@echo off
echo ============================================
echo   ETSL - Build and Run
echo ============================================
echo.

REM Check if user provided a test file
if "%1"=="" (
    echo Usage: build_and_run.bat Examples\test_file.etsl
    echo.
    echo Available test files:
    dir /b Examples\*.etsl
    exit /b 1
)

echo [1/3] Generating parser from grammar...
call .\gradlew generateParser
if %errorlevel% neq 0 (
    echo PARSER GENERATION FAILED
    exit /b 1
)
echo.

echo [2/3] Compiling Java sources...
call .\gradlew compileJava
if %errorlevel% neq 0 (
    echo COMPILATION FAILED
    exit /b 1
)
echo.

echo [3/3] Running %1...
call .\gradlew runExample --args="%1"
if %errorlevel% neq 0 (
    echo PROGRAM FAILED - Check errors above
    exit /b 1
)
echo.

echo ============================================
echo   BUILD AND RUN COMPLETE
echo ============================================