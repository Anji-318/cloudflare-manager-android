@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

for %%i in ("%CD%") do set PROJECT_NAME=%%~ni

:: 读取版本号（尝试多种方式）
set VER=unknown
if exist "app\build.gradle.kts" (
    for /f "usebackq tokens=*" %%a in (`powershell -NoProfile -Command "[regex]::Match((Get-Content 'app\build.gradle.kts' -Raw), 'versionName\s*=\s*\"([^\"]+)\"').Groups[1].Value"`) do (
        set "VER=%%a"
    )
)
if "!VER!"=="" set "VER=unknown"

set "ZIP_NAME=%PROJECT_NAME%-v!VER!-src.zip"

echo ========================================
echo   %PROJECT_NAME% 源码打包
echo   版本: !VER!
echo ========================================
echo.

if exist "!ZIP_NAME!" del /f /q "!ZIP_NAME!"

echo [打包] 正在生成 !ZIP_NAME! ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path @('app','gradle','.github','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','README.md','Android智能编译脚本_v2.0.bat','打包源码.bat') -DestinationPath '!ZIP_NAME!' -Force"

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
