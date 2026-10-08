@echo off
chcp 65001 >nul
echo ========================================
echo 修复 Android SDK 许可问题
echo ========================================
echo.

REM 设置环境变量
set "JAVA_HOME=C:\Users\jx\Documents\ruanjian\jdk-17.0.19+10"
set "ANDROID_HOME=D:\AndroidSdk"
set "ANDROID_SDK_ROOT=D:\AndroidSdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME\platform-tools;%PATH%"

echo 步骤1: 检查 Android SDK 路径
echo ANDROID_HOME = %ANDROID_HOME%
echo.

if not exist "%ANDROID_HOME%" (
    echo 错误: Android SDK 路径不存在: %ANDROID_HOME%
    pause
    exit /b 1
)

echo 步骤2: 检查 sdkmanager 工具
set "SDKMANAGER=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKMANAGER%" (
    echo 错误: sdkmanager 不存在: %SDKMANAGER%
    echo 请在 Android Studio 中安装 Android SDK Command-line Tools
    pause
    exit /b 1
)

echo 找到 sdkmanager: %SDKMANAGER%
echo.

echo 步骤3: 自动接受所有 SDK 许可协议
echo 正在接受许可...
echo.

REM 使用echo输出多个y来自动接受所有许可
(echo y
echo y
echo y
echo y
echo y
echo y
echo y
echo y
echo y
echo y) | "%SDKMANAGER%" --licenses

echo.
echo 步骤4: 安装缺失的 SDK 组件
echo 正在安装 build-tools;36.0.0 和 platforms;android-36.1...
echo.

"%SDKMANAGER%" "build-tools;36.0.0" "platforms;android-36.1"

echo.
echo ========================================
echo 完成！
echo ========================================
echo.
echo 现在可以在 Android Studio 中重新同步项目了
echo File -^> Sync Project with Gradle Files
echo.
pause
