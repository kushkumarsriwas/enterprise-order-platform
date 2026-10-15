@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Loads the demo (4 stores, 6 customers, five months of orders) into the live Render deployment, the same way
rem  run-local.cmd does locally. It first REPLACES the live marketplace data: every store, customer and order is
rem  removed (the departments and the administrator stay), so it can be run again at any time.
rem  Usage:  scripts\windows\render-demo.cmd "External Database URL" [gateway URL]
rem    External Database URL: Render dashboard > enterprise-postgres > Connect > External (keep it secret).
rem    Gateway URL: default https://enterprise-gateway.onrender.com
rem  To also publish the welcome announcement, first:  set "ADMIN_PASSWORD=the password set on enterprise-auth"
rem ===========================================================================
cd /d "%~dp0..\.."
if "%~1"=="" (
  echo Usage:  scripts\windows\render-demo.cmd "External Database URL" [gateway URL]
  echo The External Database URL is on Render: enterprise-postgres ^> Connect ^> External.
  goto failed
)
set "DB_URL=%~1"
set "GATEWAY=%~2"
if "%GATEWAY%"=="" set "GATEWAY=https://enterprise-gateway.onrender.com"

call "%~dp0ensure-docker.cmd"
if errorlevel 1 goto failed

echo.
echo This replaces every store, customer and order on %GATEWAY% with the demo.
choice /c YN /m "Continue"
if errorlevel 2 goto cancelled

echo [1/3] Removing the current marketplace data...
docker run --rm -i postgres:18 psql "%DB_URL%" -v ON_ERROR_STOP=1 -q < scripts\demo-reset.sql
if errorlevel 1 (
  echo Removing the old data FAILED - see the output above.
  goto failed
)

echo [2/3] Loading the demo through the API (sleeping services are woken first; 15-30 minutes on the free plan)...
docker run --rm -e ADMIN_PASSWORD -v "%CD%\scripts:/scripts:ro" node:22-alpine node /scripts/demo-data.mjs %GATEWAY%
if errorlevel 1 (
  echo Loading the demo FAILED - see the output above. Run this again to start over.
  goto failed
)

echo [3/3] Spreading the orders over the last five months...
docker run --rm -i postgres:18 psql "%DB_URL%" -v ON_ERROR_STOP=1 -q < scripts\demo-backdate.sql
if errorlevel 1 (
  echo Moving the orders back in time FAILED - the demo works, with every order dated today.
  goto failed
)
echo.
echo Done. Demo accounts use the password Demo-2026; the cached home page shows the new dates within 10 minutes.
exit /b 0

:cancelled
echo Nothing was changed.
exit /b 0

:failed
exit /b 1
