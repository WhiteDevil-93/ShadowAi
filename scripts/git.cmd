@echo off
setlocal

set "GIT_EXE=C:\Progra~1\Git\cmd\git.exe"
if not exist "%GIT_EXE%" (
  echo Git not found at %GIT_EXE%
  exit /b 1
)

"%GIT_EXE%" %*
set "ERR=%ERRORLEVEL%"
exit /b %ERR%

