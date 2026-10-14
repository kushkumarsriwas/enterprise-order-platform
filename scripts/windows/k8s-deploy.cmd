@echo off
setlocal EnableExtensions
rem Deploys the platform to Docker Desktop's Kubernetes (Settings > Kubernetes > Enable).
rem Uses the images built by docker compose; stops the compose containers first to free memory.
cd /d "%~dp0..\.."
kubectl version >nul 2>&1 || (echo Kubernetes is not enabled in Docker Desktop. & exit /b 1)
echo Building images...
docker compose build || exit /b 1
echo Stopping the docker compose stack to free memory (data volumes are kept)...
docker compose down
kubectl apply -k k8s || exit /b 1
echo Waiting for databases and services (5-15 minutes the first time)...
kubectl -n enterprise-platform rollout status deployment/postgres --timeout=10m
kubectl -n enterprise-platform rollout status deployment/mysql --timeout=10m
kubectl -n enterprise-platform rollout status deployment/oracle --timeout=20m
for %%D in (auth-service product-inventory-service order-service payment-service notification-service api-gateway frontend) do (
  kubectl -n enterprise-platform rollout status deployment/%%D --timeout=10m
)
kubectl -n enterprise-platform get pods -o wide
echo.
echo   UI ........... http://localhost:30000
echo   API gateway .. http://localhost:30080/swagger-ui.html
echo   Scale demo ... kubectl -n enterprise-platform scale deployment order-service --replicas=3
echo   Remove ....... kubectl delete namespace enterprise-platform
exit /b 0
