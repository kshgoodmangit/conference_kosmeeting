@rem Project-local Gradle launcher that always uses an installed Java 17 JDK.
@rem setlocal keeps JAVA_HOME changes inside this batch process and its children.
@echo off
setlocal

set "PROJECT_JAVA_HOME=D:\jdk\jdk17"
if exist "%PROJECT_JAVA_HOME%\bin\java.exe" goto runGradle

set "PROJECT_JAVA_HOME=D:\dev\jdk\jdk17"
if exist "%PROJECT_JAVA_HOME%\bin\java.exe" goto runGradle

echo ERROR: Java 17 JDK was not found in a configured project location. 1>&2
exit /b 1

:runGradle
set "JAVA_HOME=%PROJECT_JAVA_HOME%"
call "%~dp0gradlew.bat" %*
set "GRADLE_EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %GRADLE_EXIT_CODE%
