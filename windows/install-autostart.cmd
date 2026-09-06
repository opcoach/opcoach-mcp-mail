@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-autostart.ps1"
if errorlevel 1 (
  echo.
  echo Automatic startup installation failed.
  echo Press any key to close this window.
  pause >nul
  exit /b 1
)
echo.
echo Press any key to close this window.
pause >nul
