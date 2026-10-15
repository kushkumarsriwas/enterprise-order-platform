@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Used by the other scripts: makes sure the Docker engine answers. When Docker Desktop is installed but not
rem  running it is started, and the script waits for the engine (up to 5 minutes).
rem  Exit code 0 = ready, 1 = not available (the message says what to do).
rem ===========================================================================
docker info >nul 2>&1
if not errorlevel 1 goto ready

rem Find Docker Desktop: next to the docker command (...\Docker\resources\bin\docker.exe), or in the usual folders
set "DOCKER_DESKTOP="
for /f "delims=" %%D in ('where docker 2^>nul') do (
  if not defined DOCKER_DESKTOP if exist "%%~dpD..\..\Docker Desktop.exe" set "DOCKER_DESKTOP=%%~dpD..\..\Docker Desktop.exe"
)
if not defined DOCKER_DESKTOP if exist "%ProgramFiles%\Docker\Docker\Docker Desktop.exe" set "DOCKER_DESKTOP=%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
if not defined DOCKER_DESKTOP if exist "%LOCALAPPDATA%\Programs\Docker\Docker\Docker Desktop.exe" set "DOCKER_DESKTOP=%LOCALAPPDATA%\Programs\Docker\Docker\Docker Desktop.exe"
if not defined DOCKER_DESKTOP if exist "%LOCALAPPDATA%\Docker\Docker Desktop.exe" set "DOCKER_DESKTOP=%LOCALAPPDATA%\Docker\Docker Desktop.exe"
if not defined DOCKER_DESKTOP goto not_found

echo       Docker Desktop is not running - starting it (about a minute)...
start "" "%DOCKER_DESKTOP%"
set /a TRIES=0
:wait
ping -n 6 127.0.0.1 >nul
docker info >nul 2>&1
if not errorlevel 1 goto started
set /a TRIES+=1
if %TRIES% lss 60 goto wait
echo.
echo Docker Desktop did not finish starting within 5 minutes.
echo Open Docker Desktop, wait until it shows "Engine running" (bottom left), then run this again.
exit /b 1

:not_found
echo.
where docker >nul 2>&1
if errorlevel 1 goto not_installed
echo Docker is installed but its engine is not running.
echo Open Docker Desktop from the Start menu, wait until it shows "Engine running" (bottom left), then run this again.
exit /b 1

:not_installed
echo Docker Desktop was not found on this computer.
echo  - If it is installed: open it from the Start menu, wait until it shows "Engine running",
echo    then open a NEW terminal window and run this again.
echo  - If it is not installed: install it from https://www.docker.com/products/docker-desktop/
echo    (keep "Use WSL 2" ticked), restart the computer, open Docker Desktop once, then run this again.
exit /b 1

:started
echo       Docker is running.
:ready
exit /b 0
