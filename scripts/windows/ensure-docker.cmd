@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Used by the other scripts: makes sure the Docker engine answers. When Docker Desktop is not running it is
rem  started, and the script waits for the engine (up to 5 minutes). Exit code 0 = ready, 1 = not available.
rem ===========================================================================
docker info >nul 2>&1
if not errorlevel 1 goto ready

set "DOCKER_DESKTOP=%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
if not exist "%DOCKER_DESKTOP%" goto not_installed
echo       Docker Desktop is not running - starting it (about a minute)...
start "" "%DOCKER_DESKTOP%"
set /a TRIES=0
:wait
ping -n 6 127.0.0.1 >nul
docker info >nul 2>&1
if not errorlevel 1 goto started
set /a TRIES+=1
if %TRIES% lss 60 goto wait
echo Docker Desktop did not start within 5 minutes. Open it, wait until it says "Engine running", then run this again.
exit /b 1

:not_installed
echo Docker Desktop is not running. Start Docker Desktop, wait until it says "Engine running", then run this again.
exit /b 1

:started
echo       Docker is running.
:ready
exit /b 0
