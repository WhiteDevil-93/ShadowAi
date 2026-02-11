@echo off
setlocal enabledelayedexpansion

set "GIT_PATH=C:\Program Files\Git\cmd"
set "CUR_PATH="
set "REG_EXE=C:\Windows\System32\reg.exe"

if not exist "%REG_EXE%" (
  echo [setup-git-path] reg.exe not found at %REG_EXE%
  exit /b 1
)

for /f "skip=2 tokens=1,2,*" %%A in ('"%REG_EXE%" query HKCU\Environment /v Path 2^>nul') do (
  if /I "%%A"=="Path" set "CUR_PATH=%%C"
)

if not defined CUR_PATH (
  "%REG_EXE%" add HKCU\Environment /v Path /t REG_EXPAND_SZ /d "%GIT_PATH%" /f >nul
  if errorlevel 1 (
    echo [setup-git-path] Failed to update user PATH.
    exit /b 1
  )
  echo [setup-git-path] User PATH was empty. Added: %GIT_PATH%
  goto :done
)

set "FOUND_GIT="
for %%P in ("!CUR_PATH:;=" "!") do (
  if /I "%%~P"=="%GIT_PATH%" set "FOUND_GIT=1"
)
if defined FOUND_GIT (
  echo [setup-git-path] User PATH already contains: %GIT_PATH%
  goto :done
)

set "NEW_PATH=!CUR_PATH!"
if "!NEW_PATH:~-1!"==";" set "NEW_PATH=!NEW_PATH:~0,-1!"
set "NEW_PATH=!NEW_PATH!;%GIT_PATH%"

"%REG_EXE%" add HKCU\Environment /v Path /t REG_EXPAND_SZ /d "!NEW_PATH!" /f >nul
if errorlevel 1 (
  echo [setup-git-path] Failed to update user PATH.
  exit /b 1
)
echo [setup-git-path] Appended to user PATH: %GIT_PATH%

:done
echo [setup-git-path] Open a new terminal to use updated PATH.
exit /b 0
