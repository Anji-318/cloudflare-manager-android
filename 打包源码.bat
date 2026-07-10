@echo off
chcp 65001 >nul

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
if "!VER!"=="unknown" (
    echo [警告] 未读取到版本号，使用 unknown
) else (
    echo [版本] !VER!
)

echo.
echo ========================================
echo   %PROJECT_NAME% 源码打包
echo   版本: !VER!
echo ========================================
echo.

set "ZIP_NAME=%PROJECT_NAME%-v!VER!-src.zip"
if exist "!ZIP_NAME!" del /f /q "!ZIP_NAME!"

echo [打包] 正在生成 !ZIP_NAME! ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "& {$zip = '!ZIP_NAME!'; $items = @('app', 'gradle', '.github', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'README.md', 'Android智能编译脚本_v2.0.bat', '打包源码.bat'); Compress-Archive -Path $items -DestinationPath $zip -Force; $size = (Get-Item $zip).Length; Write-Host ('[成功] 源码包已生成: ' + $zip); Write-Host ('       大小: ' + [math]::Round($size/1KB, 2) + ' KB')}"

echo.
echo 按任意键退出...
pause >nul
