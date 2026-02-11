@echo off
setlocal

rem Bootstrap a predictable cmd session for ShadowAi development.
set "REPO_ROOT=%~dp0.."
set "PATH=%PATH%;C:\Windows\System32;C:\Windows;C:\Progra~1\Git\cmd;C:\Progra~1\Git\bin"

cd /d "%REPO_ROOT%"

echo [ShadowAi] Repo root: %CD%
git --version >nul 2>nul
if errorlevel 1 (
  echo [ShadowAi] Git not found on PATH. Expected C:\Program Files\Git.
) else (
  for /f "delims=" %%g in ('git --version') do echo [ShadowAi] %%g
)

echo [ShadowAi] Ready. Build commands require explicit user approval.
cmd /k

