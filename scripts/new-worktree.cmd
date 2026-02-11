@echo off
setlocal enabledelayedexpansion

if "%~1"=="" goto :usage

set "NAME=%~1"
set "BASE=%~2"
if "%BASE%"=="" set "BASE=master"
set "CUSTOM_PATH=%~3"

set "REPO_ROOT=%~dp0.."
pushd "%REPO_ROOT%" >nul

call scripts\git.cmd rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
  echo [new-worktree] Not inside a Git repository: %REPO_ROOT%
  popd >nul
  exit /b 1
)

call scripts\git.cmd rev-parse --verify "%BASE%" >nul 2>nul
if errorlevel 1 (
  echo [new-worktree] Base ref not found: %BASE%
  popd >nul
  exit /b 1
)

set "BRANCH=%NAME%"
echo %BRANCH% | C:\Windows\System32\findstr /c:"/" >nul
if errorlevel 1 (
  set "BRANCH=feature/%NAME%"
)

call scripts\git.cmd show-ref --verify --quiet "refs/heads/%BRANCH%"
if not errorlevel 1 (
  echo [new-worktree] Branch already exists: %BRANCH%
  echo [new-worktree] Pick another name or remove the branch first.
  popd >nul
  exit /b 1
)

if not "%CUSTOM_PATH%"=="" (
  set "WT_PATH=%CUSTOM_PATH%"
) else (
  for %%I in ("%REPO_ROOT%") do (
    set "ROOT_DIR=%%~nI"
    set "ROOT_PARENT=%%~dpI"
  )
  set "PATH_SEG=%NAME%"
  set "PATH_SEG=!PATH_SEG:/=-!"
  set "PATH_SEG=!PATH_SEG:\=-!"
  set "PATH_SEG=!PATH_SEG: =-!"
  set "WT_PATH=!ROOT_PARENT!!ROOT_DIR!-wt-!PATH_SEG!"
)

if exist "%WT_PATH%" (
  echo [new-worktree] Target path already exists: %WT_PATH%
  echo [new-worktree] Provide a third argument with a custom path.
  popd >nul
  exit /b 1
)

call scripts\git.cmd worktree add -b "%BRANCH%" "%WT_PATH%" "%BASE%"
if errorlevel 1 (
  echo [new-worktree] Failed to create worktree.
  popd >nul
  exit /b 1
)

echo.
echo [new-worktree] Created branch  : %BRANCH%
echo [new-worktree] Base commit     : %BASE%
echo [new-worktree] Worktree path   : %WT_PATH%
echo.
echo Next:
echo   cd /d "%WT_PATH%"
echo   ..\ShadowAi\scripts\git.cmd status --short --branch

popd >nul
exit /b 0

:usage
echo Usage:
echo   scripts\new-worktree.cmd ^<name^> [base] [path]
echo.
echo Examples:
echo   scripts\new-worktree.cmd phase3-cleanup
echo   scripts\new-worktree.cmd feature/pii-hardening master
echo   scripts\new-worktree.cmd phase4-security master c:\Users\anon3\Downloads\ShadowAi-wt-security
exit /b 1

