@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Builds and starts the WHOLE Nexora platform in Docker, and loads the demo the first time.
rem  Usage (from anywhere):  scripts\windows\run-local.cmd
rem  Needs Docker Desktop (started automatically when it is not running). Secrets live in .env (git-ignored);
rem  missing ones are generated here.
rem ===========================================================================
cd /d "%~dp0..\.."

echo.
echo [1/6] Checking Docker...
call "%~dp0ensure-docker.cmd"
if errorlevel 1 goto failed

echo [2/6] Removing containers and images of older versions of this project (if any)...
for %%C in (enterprise-gateway enterprise-auth enterprise-order enterprise-product-inventory enterprise-payment enterprise-notification enterprise-oracle enterprise-redis enterprise-redis-compose enterprise-postgres) do (
  docker rm -f %%C >nul 2>&1
)
docker compose -p infrastructure down >nul 2>&1
rem Images of older versions (1.x, 2.x) are no longer used: remove them, and dangling layers of earlier builds
for /f "tokens=1,2" %%A in ('docker images --filter "reference=enterprise/*" --format "{{.Tag}} {{.Repository}}:{{.Tag}}" 2^>nul') do (
  if not "%%A"=="3.0.0" docker rmi %%B >nul 2>&1
)
docker image prune -f >nul 2>&1
kubectl get namespace enterprise-platform >nul 2>&1
if not errorlevel 1 (
  echo       The Kubernetes deployment is running - removing it to free memory ^(re-create it with scripts\windows\k8s-deploy.cmd^)
  kubectl delete namespace enterprise-platform --wait=false >nul 2>&1
)

echo [3/6] Secrets and local data...
if not exist ".env" copy /y ".env.example" ".env" >nul
rem The administrator password and the service-to-service key are generated once and kept in .env (never in git).
powershell -NoProfile -ExecutionPolicy Bypass -Command "$f='.env'; $c=@(Get-Content $f); $n=0; foreach($k in @('ADMIN_PASSWORD','INTERNAL_API_KEY')){ if(-not ($c | Where-Object { $_ -match ('^'+$k+'=\S') })){ if($k -eq 'ADMIN_PASSWORD'){ $v='Nexora-'+[guid]::NewGuid().ToString('N').Substring(0,12) } else { $v=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N') }; $c=@($c | Where-Object { $_ -notmatch ('^'+$k+'=') })+($k+'='+$v); $n++ } }; if($n -gt 0){ Set-Content -Path $f -Value $c -Encoding ascii; Write-Host ('      generated '+$n+' new secret(s) in .env') }"
if errorlevel 1 (
  echo Could not update .env. Set ADMIN_PASSWORD and INTERNAL_API_KEY in .env by hand ^(see .env.example^) and run this again.
  goto failed
)
set "ADMIN_PASSWORD="
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do if /i "%%A"=="ADMIN_PASSWORD" set "ADMIN_PASSWORD=%%B"

if not exist ".local" mkdir ".local"
set "DATA_VERSION=0"
if exist ".local\data-version" set /p DATA_VERSION=<".local\data-version"
rem Raised whenever the demo or the tables change; the local data is then deleted once and the demo loaded again
if not "%DATA_VERSION%"=="4" (
  echo       This version comes with a new demo, so the OLD local test data is deleted once.
  docker compose down -v --remove-orphans >nul 2>&1
  if exist ".local\demo-loaded" del ".local\demo-loaded"
  >".local\data-version" echo 4
)

echo [4/6] Building images and starting 12 containers (first run: 10-25 minutes, Oracle is slow to initialise)...
docker compose up -d --build --wait --wait-timeout 1800
if errorlevel 1 (
  echo.
  echo Some containers did not become healthy. Current state:
  docker compose ps
  echo Logs of a service:  docker compose logs --tail 100 payment-service
  goto failed
)

if exist ".local\demo-loaded" (
  echo [5/6] Demo data is already loaded ^(delete .local\demo-loaded and the database volumes to start over^).
  goto summary
)
echo [5/6] Loading the demo through the API: 4 stores, 6 customers, five months of orders (5-10 minutes)...
docker run --rm --network enterprise-order-platform_default -e ADMIN_PASSWORD -v "%CD%\scripts:/scripts:ro" node:22-alpine node /scripts/demo-data.mjs http://api-gateway:8080
if errorlevel 1 (
  echo Loading the demo FAILED - see the output above. The platform itself is running; run this script again to retry.
  goto summary
)
echo       Spreading the orders over the last five months...
docker compose exec -T postgres psql -U postgres -d enterprise -v ON_ERROR_STOP=1 -q < scripts\demo-backdate.sql
if errorlevel 1 (
  echo Moving the demo orders back in time FAILED - see the output above. The demo works, with every order dated today.
  >".local\demo-loaded" echo loaded
  goto summary
)
>".local\demo-loaded" echo loaded
rem Payments live in Oracle and accounts in MySQL: demo-backdate.sql wrote the same changes for them as SQL files.
docker compose exec -T postgres cat /tmp/demo-backdate-oracle.sql | docker compose exec -T oracle bash -c "sqlplus -s -L payment/$APP_USER_PASSWORD@//localhost:1521/FREEPDB1"
if errorlevel 1 echo       WARNING: the payment dates in Oracle were not moved; the demo still works.
docker compose exec -T postgres cat /tmp/demo-backdate-mysql.sql | docker compose exec -T mysql sh -c "MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot auth_db"
if errorlevel 1 echo       WARNING: the account dates in MySQL were not moved; the demo still works.
rem The product cache still holds the old dates ("new arrivals", home page)
docker compose exec -T redis redis-cli FLUSHALL >nul

:summary
echo.
echo [6/6] Nexora is running:
echo   Marketplace + Seller Central .. http://localhost:3000
echo   Control Center (admin) ........ http://localhost:3001    user: admin    password: %ADMIN_PASSWORD%
echo   API gateway + Swagger ......... http://localhost:8080/swagger-ui.html
echo   Demo accounts (password Demo-2026):
echo     stores ..... alnoor, saharahome, oudamber, falconsports   (sign in on "Seller Central")
echo     customers .. aisha, rahul, fatima, omar, maria, hamad
echo   Databases ..................... PostgreSQL localhost:5433, MySQL localhost:3307, Oracle localhost:1522, Redis localhost:6380
echo   End-to-end test ............... scripts\windows\smoke-test.cmd
echo   Stop everything ............... docker compose down     (add -v to delete the data as well)
start "" http://localhost:3000
exit /b 0

:failed
exit /b 1
