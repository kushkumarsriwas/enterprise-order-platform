@echo off
setlocal EnableExtensions
rem Pushes the committed work to GitHub. GitHub Actions then tests everything and deploys to Render.
cd /d "%~dp0..\.."
git add -A
git diff --cached --quiet || git commit -m "Update project files"
git push origin main
if errorlevel 1 (
  echo Push failed. If GitHub asks you to sign in, complete the sign-in window and run this again.
  exit /b 1
)
echo.
echo Pushed. Follow the pipeline here: https://github.com/kushkumarsriwas/enterprise-order-platform/actions
start "" https://github.com/kushkumarsriwas/enterprise-order-platform/actions
exit /b 0
