@echo off
setlocal
set "INSTALLER=%~dp0..\.tools\downloads\Docker Desktop Installer.exe"
if not exist "%INSTALLER%" (
  echo Docker Desktop installer was not found: "%INSTALLER%"
  exit /b 1
)
echo Installing Docker Desktop for the current Windows user...
"%INSTALLER%" install --user --backend=wsl-2 --accept-license
if errorlevel 1 (
  echo Docker Desktop installation failed. Review %%LOCALAPPDATA%%\Docker\install-log.txt
  exit /b %ERRORLEVEL%
)
echo Docker Desktop installation completed. Restart Windows before starting the backend.
