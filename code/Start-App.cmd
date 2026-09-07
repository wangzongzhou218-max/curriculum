@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-App.ps1" %*
exit /b %ERRORLEVEL%
