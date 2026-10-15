@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Optional: loads the demo (4 stores, 6 customers, five months of orders) into the live Render deployment,
rem  the same way run-local.cmd does locally. Run it once; it does nothing when the demo stores already exist.
rem  Usage:  scripts\windows\render-demo.cmd "External Database URL" [gateway URL]
rem    External Database URL: Render dashboard > enterprise-postgres > Connect > External (keep it secret).
rem    Gateway URL: default https://enterprise-gateway.onrender.com
rem  To also publish the welcome announcement, first:  set ADMIN_PASSWORD=the password set on enterprise-auth
rem ===========================================================================
cd /d "%~dp0..\.."
if "%~1"=="" (
  echo Usage:  scripts\windows\render-demo.cmd "External Database URL" [gateway URL]
  echo The External Database URL is on Render: enterprise-postgres ^> Connect ^> External.
  exit /b 1
)
set "DB_URL=%~1"
set "GATEWAY=%~2"
if "%GATEWAY%"=="" set "GATEWAY=https://enterprise-gateway.onrender.com"

docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start it and run this again.
  exit /b 1
)

echo Checking whether the demo is already on %GATEWAY% (the free services can take a minute to wake up)...
docker run --rm -e GATEWAY="%GATEWAY%" node:22-alpine node -e "(async () => { for (let i = 0; i < 12; i++) { try { const r = await fetch(process.env.GATEWAY + '/api/sellers?search=Al%%20Noor'); if (r.ok) { const d = await r.json(); process.exit(d.totalElements > 0 ? 0 : 1); } } catch (e) {} await new Promise((s) => setTimeout(s, 15000)); } process.exit(2); })()"
if errorlevel 2 (
  echo The gateway did not answer. Open %GATEWAY%/actuator/health in a browser, wait until it shows UP, then run this again.
  exit /b 1
)
if not errorlevel 1 (
  echo The demo stores already exist on Render - nothing to do.
  exit /b 0
)

echo Loading the demo through the API (10-20 minutes on the free plan)...
docker run --rm -e ADMIN_PASSWORD -v "%CD%\scripts:/scripts:ro" node:22-alpine node /scripts/demo-data.mjs %GATEWAY%
if errorlevel 1 (
  echo Loading the demo FAILED - see the output above.
  exit /b 1
)
echo Spreading the orders over the last five months...
docker run --rm -i postgres:18 psql "%DB_URL%" -v ON_ERROR_STOP=1 -q < scripts\demo-backdate.sql
if errorlevel 1 (
  echo Moving the orders back in time FAILED - the demo works, with every order dated today.
  exit /b 1
)
echo.
echo Done. The cached home page shows the new dates within 10 minutes. Demo accounts use the password Demo-2026.
exit /b 0
