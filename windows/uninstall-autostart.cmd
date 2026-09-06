@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall-autostart.ps1"
if errorlevel 1 (
  echo.
  echo Automatic startup removal failed.
  echo Press any key to close this window.
  pause >nul
  exit /b 1
)
echo.
echo Press any key to close this window.
pause >nul
