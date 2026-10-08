@echo off
REM 自动接受Android SDK许可协议

set "JAVA_HOME=C:\Users\jx\Documents\ruanjian\jdk-17.0.19+10"
set "ANDROID_HOME=D:\AndroidSdk"
set "ANDROID_SDK_ROOT=D:\AndroidSdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo 正在接受Android SDK许可协议...
echo.

REM 使用echo输出多个y来自动接受所有许可
(echo y & echo y & echo y & echo y & echo y & echo y & echo y & echo y & echo y & echo y) | "%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat" --licenses

echo.
echo 许可协议接受完成！
pause
