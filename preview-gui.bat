@echo off
setlocal

set "preview_java="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "preview_java=%JAVA_HOME%\bin\java.exe"
if not defined preview_java (
    where java.exe >nul 2>nul && set "preview_java=java.exe"
)
if not defined preview_java (
    echo Galaxia GUI Preview requires JDK 25. Set JAVA_HOME or add java to PATH. 1>&2
    exit /b 2
)

"%preview_java%" "%~dp0tools\gui-preview\PreviewBootstrap.java" "%~dp0." %*
exit /b %ERRORLEVEL%
