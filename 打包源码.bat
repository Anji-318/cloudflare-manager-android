@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

for %%i in ("%CD%") do set PROJECT_NAME=%%~ni

set VER=unknown
for /f "tokens=2 delims==" %%a in ('findstr /C:"versionName = " app\build.gradle.kts') do (
    set "VER=%%a"
    set "VER=!VER: =!"
    set "VER=!VER:"=!"
    goto :got_ver
)
:got_ver

if "!VER!"=="" set "VER=unknown"

echo ========================================
echo   %PROJECT_NAME% 源码打包
echo   版本: !VER!
echo ========================================
echo.

set "ZIP_NAME=%PROJECT_NAME%-v!VER!-src.zip"
if exist "!ZIP_NAME!" del /f /q "!ZIP_NAME!"

echo [打包] 正在生成 !ZIP_NAME! ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'app\src','app\build.gradle.kts','app\proguard-rules.pro','gradle\wrapper\gradle-wrapper.jar','gradle\wrapper\gradle-wrapper.properties','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','README.md' -DestinationPath '!ZIP_NAME!' -Force"

if exist "!ZIP_NAME!" (
    for %%F in ("!ZIP_NAME!") do set "FSIZE=%%~zF"
    echo [成功] 源码包已生成: !ZIP_NAME!
    echo        大小: !FSIZE! bytes
) else (
    echo [错误] 打包失败
)

echo.
echo 按任意键退出...
pause >nul
