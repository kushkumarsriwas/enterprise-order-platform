@echo off
setlocal EnableExtensions
rem ===========================================================================
rem  Pushes the branches (main, develop, feature/*) and the release tags to GitHub.
rem  GitHub Actions then tests every pushed branch (ci.yml) and deploys main when it passes (cd.yml).
rem  Secrets never leave this computer: .env, .local\ and k8s\config\secrets.env are git-ignored.
rem  Usage:  scripts\windows\publish.cmd
rem ===========================================================================
cd /d "%~dp0..\.."

rem Anything changed by hand is committed on the current branch first
git add -A
git diff --cached --quiet
if errorlevel 1 git commit -m "Update project files"

git push origin --all
if errorlevel 1 (
  echo Push failed. If GitHub asks you to sign in, complete the sign-in window and run this again.
  goto failed
)
git push origin --tags
if errorlevel 1 (
  echo Pushing the tags failed - run this again.
  goto failed
)
echo.
echo Pushed. Follow the pipeline here: https://github.com/kushkumarsriwas/enterprise-order-platform/actions
start "" https://github.com/kushkumarsriwas/enterprise-order-platform/actions
exit /b 0

:failed
exit /b 1
