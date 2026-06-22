@echo off
set "REPO_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%REPO_DIR%bin\manager.ps1"
