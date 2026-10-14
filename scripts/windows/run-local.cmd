@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Builds and starts the WHOLE platform in Docker, then runs the end-to-end test.
rem  Usage (from anywhere):  scripts\windows\run-local.cmd
rem ===========================================================================
cd /d "%~dp0..\.."

echo.
echo [1/5] Checking Docker...
docker info >nul 2>&1
if errorlevel 1 (
  echo Docker Desktop is not running. Start Docker Desktop, wait until it says "Engine running", then run this again.
  exit /b 1
)

echo [2/5] Removing containers of the OLD version of this project (if any)...
for %%C in (enterprise-gateway enterprise-auth enterprise-order enterprise-product-inventory enterprise-payment enterprise-notification enterprise-oracle enterprise-redis enterprise-redis-compose enterprise-postgres) do (
  docker rm -f %%C >nul 2>&1
)
docker compose -p infrastructure down >nul 2>&1
kubectl get namespace enterprise-platform >nul 2>&1
if not errorlevel 1 (
  echo       Old Kubernetes deployment found - removing it to free memory ^(re-create with scripts\windows\k8s-deploy.cmd^)
  kubectl delete namespace enterprise-platform --wait=false >nul 2>&1
)

echo [3/5] Building images and starting 11 containers (first run: 10-20 minutes, Oracle is slow to initialise)...
docker compose up -d --build --wait --wait-timeout 1500
if errorlevel 1 (
  echo.
  echo Some containers did not become healthy. Current state:
  docker compose ps
  echo Logs of a service:  docker compose logs --tail 100 payment-service
  exit /b 1
)

echo [4/5] Running the end-to-end smoke test through the API gateway...
docker run --rm --network enterprise-order-platform_default -v "%CD%\scripts:/scripts:ro" alpine:3.20 sh -c "apk add --no-cache bash curl jq >/dev/null 2>&1 && bash /scripts/smoke-test.sh http://api-gateway:8080"
if errorlevel 1 (
  echo Smoke test FAILED - see the output above.
  exit /b 1
)

echo.
echo [5/5] Everything is up:
echo   Shop / admin UI ..... http://localhost:3000   (admin / Admin@12345, customer / Customer@12345)
echo   API gateway ......... http://localhost:8080
echo   Swagger (all APIs) .. http://localhost:8080/swagger-ui.html
echo   Databases ........... PostgreSQL localhost:5433, MySQL localhost:3307, Oracle localhost:1522, Redis localhost:6380
echo   Stop everything ..... docker compose down
start "" http://localhost:3000
exit /b 0
