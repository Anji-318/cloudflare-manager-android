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

:: 创建临时目录，只复制需要的文件
set "TMP_DIR=%TEMP%\src_pkg_%RANDOM%"
mkdir "%TMP_DIR%\app\src" >nul 2>&1
mkdir "%TMP_DIR%\gradle\wrapper" >nul 2>&1
xcopy /E /I /Q "app\src" "%TMP_DIR%\app\src" >nul 2>&1
copy /Y "app\build.gradle.kts" "%TMP_DIR%\app\" >nul 2>&1
copy /Y "app\proguard-rules.pro" "%TMP_DIR%\app\" >nul 2>&1
xcopy /E /I /Q "gradle\wrapper" "%TMP_DIR%\gradle\wrapper" >nul 2>&1
copy /Y "gradle.properties" "%TMP_DIR%\" >nul 2>&1
copy /Y "build.gradle.kts" "%TMP_DIR%\" >nul 2>&1
copy /Y "settings.gradle.kts" "%TMP_DIR%\" >nul 2>&1
copy /Y "gradlew" "%TMP_DIR%\" >nul 2>&1
copy /Y "gradlew.bat" "%TMP_DIR%\" >nul 2>&1
copy /Y "README.md" "%TMP_DIR%\" >nul 2>&1

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%TMP_DIR%\*' -DestinationPath '!ZIP_NAME!' -Force"

rmdir /S /Q "%TMP_DIR%" >nul 2>&1

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
