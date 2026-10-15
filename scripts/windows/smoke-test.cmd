@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  End-to-end test of the running platform (start it first with scripts\windows\run-local.cmd).
rem  Runs scripts/smoke-test.mjs in a Node container on the docker compose network: two test stores and two test
rem  customers go through sign-up, products, checkout, payment, delivery, refund, reviews and the administrator.
rem  Afterwards the test products are deleted and the test accounts suspended (they stay visible in the Control
rem  Center under "Suspended"). GitHub Actions runs the same test on every push.
rem  Usage:  scripts\windows\smoke-test.cmd
rem ===========================================================================
cd /d "%~dp0..\.."

call "%~dp0ensure-docker.cmd"
if errorlevel 1 goto failed
set "ADMIN_PASSWORD="
if exist ".env" for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do if /i "%%A"=="ADMIN_PASSWORD" set "ADMIN_PASSWORD=%%B"

docker run --rm --network enterprise-order-platform_default -e ADMIN_PASSWORD -e KEEP_TEST_DATA -v "%CD%\scripts:/scripts:ro" node:22-alpine node /scripts/smoke-test.mjs http://api-gateway:8080
if errorlevel 1 (
  echo.
  echo Smoke test FAILED - see the output above. Logs of a service:  docker compose logs --tail 100 order-service
  goto failed
)
exit /b 0

:failed
exit /b 1
