@echo off
setlocal enabledelayedexpansion

REM Zed IDE Setup Script for OBP-API (Windows)
REM This script copies the recommended Zed configuration to your local .zed folder

echo 🔧 Setting up Zed IDE configuration for OBP-API...

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
set "ZED_DIR=%PROJECT_ROOT%\.zed"

REM Create .zed directory if it doesn't exist
if not exist "%ZED_DIR%" (
    echo 📁 Creating .zed directory...
    mkdir "%ZED_DIR%"
) else (
    echo 📁 .zed directory already exists
)

REM Copy settings.json
if exist "%SCRIPT_DIR%settings.json" (
    echo ⚙️  Copying settings.json...
    copy "%SCRIPT_DIR%settings.json" "%ZED_DIR%\settings.json" >nul
    if !errorlevel! equ 0 (
        echo ✅ settings.json copied successfully
    ) else (
        echo ❌ Error copying settings.json
        exit /b 1
    )
) else (
    echo ❌ Error: settings.json not found in zed folder
    exit /b 1
)

REM Copy tasks.json
if exist "%SCRIPT_DIR%tasks.json" (
    echo 📋 Copying tasks.json...
    copy "%SCRIPT_DIR%tasks.json" "%ZED_DIR%\tasks.json" >nul
    if !errorlevel! equ 0 (
        echo ✅ tasks.json copied successfully
    ) else (
        echo ❌ Error copying tasks.json
        exit /b 1
    )
) else (
    echo ❌ Error: tasks.json not found in zed folder
    exit /b 1
)

echo.
echo 🎉 Zed IDE setup completed successfully!
echo.
echo Your Zed configuration includes:
echo   • Format on save: DISABLED (preserves your code formatting)
echo   • Scala/Metals LSP configuration optimized for OBP-API
echo   • 9 predefined tasks for building, running, and testing
echo.
echo To see available tasks in Zed, use: Ctrl + Shift + P → 'task: spawn'
echo.
echo Note: The .zed folder is in .gitignore, so you can customize settings
echo       without affecting other developers.

pause