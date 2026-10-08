@echo off
REM ========================================
REM ICU APK Build Script
REM Solves AGP 9.x SdkLocator strict validation
REM ========================================
REM Usage:
REM   Double-click to run, or:
REM     build.bat         -> build debug
REM     build.bat release -> build release
REM     build.bat clean   -> clean only
REM     build.bat rebuild -> clean + debug
REM
REM IMPORTANT:
REM   DO NOT run gradlew.bat directly from Android Studio!
REM   It triggers AGP 9.x SdkLocator validation that requires
REM   specific SDK layout (cmdline-tools/latest etc).
REM   This script bypasses that by using ANDROID_HOME only.

setlocal EnableExtensions

REM Paths for THIS machine.
REM   SDK: D:\AndroidSdk  (NOT D:\Users\DevEcoStudio\Sdk -- that is the
REM        HarmonyOS SDK and has no platforms\ subdirectory)
REM   JDK: AGP 9.x requires a JDK 21 toolchain, and it must NOT be a JetBrains
REM        JBR -- Gradle 9.4.1 queries for nativeImageCapable=false, which JBR
REM        metadata does not satisfy, so JBR 21 is detected then rejected.
REM        Install a standalone Temurin/Oracle JDK 21 and set the path below.
REM        Available on this machine (none usable): JBR 25 at D:\Users\Android\jbr,
REM        JBR 21 at "D:\Users\DevEcoStudio\DevEco Studio\jbr", Adoptium 17 at
REM        C:\Users\jx\Documents\ruanjian\jdk-17.0.19+10 (too old).
set "JAVA_HOME=C:\Users\jx\Documents\ruanjian\jdk-17.0.19+10"
set "ANDROID_HOME=D:\AndroidSdk"
set "ANDROID_SDK_ROOT=D:\AndroidSdk"
set "PATH=%JAVA_HOME%\bin;D:\AndroidSdk\platform-tools;%PATH%"

cd /d "%~dp0"

REM Fail fast with a clear message if either path is wrong, instead of
REM letting AGP emit a confusing SdkLocator error much later.
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: JDK not found at %JAVA_HOME%
    echo        Edit JAVA_HOME at the top of this script.
    pause
    exit /b 1
)
if not exist "%ANDROID_HOME%\platforms" (
    echo ERROR: Android SDK not found at %ANDROID_HOME%
    echo        Expected a platforms\ subdirectory.
    echo        Note: D:\Users\DevEcoStudio\Sdk is the HarmonyOS SDK, not this.
    pause
    exit /b 1
)

REM AGP 9.x requires ANDROID_HOME env var, not local.properties
if exist "local.properties" (
    echo Removing local.properties (AGP 9.x SdkLocator strict validation)
    del /F /Q "local.properties"
)

REM Stop any stale Gradle daemon that may have cached old SDK paths
call "%~dp0gradlew.bat" --stop >nul 2>&1

echo ========================================
echo JAVA_HOME    = %JAVA_HOME%
echo ANDROID_HOME = %ANDROID_HOME%
echo Working dir  = %CD%
echo ========================================
echo.

set "BUILD_TASK=assembleDebug"
if /i "%~1"=="release" set "BUILD_TASK=assembleRelease"
if /i "%~1"=="clean" set "BUILD_TASK=clean"
if /i "%~1"=="rebuild" set "BUILD_TASK=clean assembleDebug"

echo Running: gradlew.bat %BUILD_TASK%
echo.

call "%~dp0gradlew.bat" %BUILD_TASK% --no-daemon --console=plain
set "GRADLE_EXIT=%ERRORLEVEL%"

echo.
echo ========================================
if %GRADLE_EXIT% neq 0 (
    echo BUILD FAILED! Exit code: %GRADLE_EXIT%
) else (
    echo BUILD SUCCESS!
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        copy /Y "app\build\outputs\apk\debug\app-debug.apk" "..\icu-debug.apk" >nul
        echo Copied: app-debug.apk -^> ..\icu-debug.apk
    )
    if exist "app\build\outputs\apk\release\app-release.apk" (
        copy /Y "app\build\outputs\apk\release\app-release.apk" "..\icu-release.apk" >nul
        echo Copied: app-release.apk -^> ..\icu-release.apk
    )
)
echo ========================================
pause
endlocal