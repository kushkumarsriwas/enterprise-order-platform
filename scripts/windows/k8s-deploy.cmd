@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Deploys Nexora to the Kubernetes cluster of Docker Desktop (Settings > Kubernetes > Enable Kubernetes).
rem  Usage:  scripts\windows\k8s-deploy.cmd
rem  Builds the images with docker compose, stops the compose containers to free memory, applies k8s/ with
rem  kustomize, waits for every pod and loads the demo. Remove it again:  kubectl delete namespace enterprise-platform
rem ===========================================================================
cd /d "%~dp0..\.."
set "NS=enterprise-platform"

kubectl version >nul 2>&1
if errorlevel 1 (
  echo Kubernetes is not running. In Docker Desktop open Settings ^> Kubernetes ^> Enable Kubernetes, wait until it is green, then run this again.
  exit /b 1
)

echo [1/6] Secrets: k8s\config\secrets.env from .env ^(both git-ignored^)...
if not exist ".env" copy /y ".env.example" ".env" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$f='.env'; $c=@(Get-Content $f); $n=0; foreach($k in @('ADMIN_PASSWORD','INTERNAL_API_KEY')){ if(-not ($c | Where-Object { $_ -match ('^'+$k+'=\S') })){ if($k -eq 'ADMIN_PASSWORD'){ $v='Nexora-'+[guid]::NewGuid().ToString('N').Substring(0,12) } else { $v=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N') }; $c=@($c | Where-Object { $_ -notmatch ('^'+$k+'=') })+($k+'='+$v); $n++ } }; if($n -gt 0){ Set-Content -Path $f -Value $c -Encoding ascii }"
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "$e=@{}; Get-Content .env | Where-Object { $_ -match '^[A-Z_]+=' } | ForEach-Object { $p=$_.Split('=',2); $e[$p[0]]=$p[1] }; $d=[ordered]@{POSTGRES_PASSWORD='postgres'; MYSQL_ROOT_PASSWORD='root_pass'; MYSQL_PASSWORD='auth_pass'; ORACLE_PASSWORD='oracle_admin'; ORACLE_APP_PASSWORD='payment_pass'; ADMIN_PASSWORD=''; INTERNAL_API_KEY=''}; $out=foreach($k in $d.Keys){ $v=$e[$k]; if(-not $v){ $v=$d[$k] }; $k+'='+$v }; Set-Content -Path 'k8s\config\secrets.env' -Value $out -Encoding ascii"
if errorlevel 1 exit /b 1
set "ADMIN_PASSWORD="
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do if /i "%%A"=="ADMIN_PASSWORD" set "ADMIN_PASSWORD=%%B"

echo [2/6] Building the images (docker compose build)...
docker compose build
if errorlevel 1 exit /b 1

echo [3/6] Stopping the docker compose containers to free memory (their data is kept)...
docker compose down

echo [4/6] Applying the manifests (kubectl apply -k k8s)...
kubectl apply -k k8s
if errorlevel 1 exit /b 1

echo [5/6] Waiting for the databases and services (first time 5-20 minutes, Oracle is the slowest)...
for %%D in (postgres mysql redis oracle auth-service product-service order-service payment-service notification-service api-gateway frontend admin-frontend) do (
  kubectl -n %NS% rollout status deployment/%%D --timeout=20m
  if errorlevel 1 (
    echo %%D did not become ready. Look at:  kubectl -n %NS% describe pod -l app=%%D
    exit /b 1
  )
)
kubectl -n %NS% get pods -o wide

echo [6/6] Demo data...
rem Already there? (the demo store "Al Noor Electronics" exists)
docker run --rm node:22-alpine node -e "fetch('http://host.docker.internal:30080/api/sellers?search=Al%%20Noor').then(r => r.json()).then(d => process.exit(d.totalElements > 0 ? 0 : 1)).catch(() => process.exit(1))"
if not errorlevel 1 (
  echo       The demo is already loaded.
  goto summary
)
docker run --rm -e ADMIN_PASSWORD -v "%CD%\scripts:/scripts:ro" node:22-alpine node /scripts/demo-data.mjs http://host.docker.internal:30080
if errorlevel 1 (
  echo Loading the demo FAILED - see the output above. The platform itself is running.
  goto summary
)
kubectl -n %NS% exec -i deploy/postgres -- psql -U postgres -d enterprise -v ON_ERROR_STOP=1 -q < scripts\demo-backdate.sql
if errorlevel 1 (
  echo Moving the demo orders back in time FAILED - the demo works, with every order dated today.
  goto summary
)
kubectl -n %NS% exec deploy/postgres -- cat /tmp/demo-backdate-oracle.sql | kubectl -n %NS% exec -i deploy/oracle -- bash -c "sqlplus -s -L payment/$APP_USER_PASSWORD@//localhost:1521/FREEPDB1"
if errorlevel 1 echo       WARNING: the payment dates in Oracle were not moved; the demo still works.
kubectl -n %NS% exec deploy/postgres -- cat /tmp/demo-backdate-mysql.sql | kubectl -n %NS% exec -i deploy/mysql -- sh -c "MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot auth_db"
if errorlevel 1 echo       WARNING: the account dates in MySQL were not moved; the demo still works.
kubectl -n %NS% exec deploy/redis -- redis-cli FLUSHALL >nul

:summary
echo.
echo Nexora is running on Kubernetes (namespace %NS%):
echo   Marketplace + Seller Central .. http://localhost:30000
echo   Control Center (admin) ........ http://localhost:30001    user: admin    password: %ADMIN_PASSWORD%
echo   API gateway + Swagger ......... http://localhost:30080/swagger-ui.html
echo   Pods and services ............. kubectl -n %NS% get pods,services
echo   Scale out ..................... kubectl -n %NS% scale deployment order-service --replicas=3
echo   Self-healing .................. kubectl -n %NS% delete pod -l app=order-service    (a new pod replaces it)
echo   Rolling update ................ kubectl -n %NS% rollout restart deployment/order-service
echo   Autoscaling (needs metrics-server) .. kubectl -n %NS% get hpa
echo   Remove everything ............. kubectl delete namespace %NS%
start "" http://localhost:30000
exit /b 0
