@echo off
REM Build the app and install it on a USB-connected phone.
REM Double-click this file, or run it from a terminal.
setlocal
cd /d "%~dp0.."

where adb >nul 2>&1
if errorlevel 1 (
  echo adb not found. Install Android platform-tools first.
  pause
  exit /b 1
)

echo Building...
call gradlew.bat --no-daemon assembleDebug
if errorlevel 1 (
  echo Build failed. See the errors above.
  pause
  exit /b 1
)

echo Installing...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
  echo Install failed. Enable USB debugging and accept the prompt on the phone.
  pause
  exit /b 1
)

echo Done. The ARCHIVE icon is now on the phone.
pause
