@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

for %%i in ("%CD%") do set PROJECT_NAME=%%~ni

:: 读取版本号
set VER=unknown
if exist "app\build.gradle.kts" (
    for /f "tokens=2 delims==" %%a in ('findstr "versionName" app\build.gradle.kts') do (
        set VER=%%a
        set VER=!VER: =!
        set VER=!VER:"=!
    )
)

set "ZIP_NAME=%PROJECT_NAME%-v%VER%-src.zip"
echo ========================================
echo   %PROJECT_NAME% 源码打包
echo   版本: %VER%
echo ========================================
echo.

if exist "%ZIP_NAME%" del /f /q "%ZIP_NAME%"

echo [打包] 正在生成 %ZIP_NAME% ...
echo.

powershell -NoProfile -Command "& {
    $exclude = @('.git', '.gradle', '.idea', 'build', '.kotlin', 'release', '*.apk', '*.jks', '*-key.txt', 'local.properties', '.gitignore', 'git-config.txt', '强制推送脚本.bat', '会话记录备份.md');
    $items = Get-ChildItem -Path '.' | Where-Object {
        $item = $_;
        $skip = $false;
        foreach ($pattern in $exclude) {
            if ($item.Name -like $pattern) { $skip = $true; break }
        }
        -not $skip
    };
    if ($items) {
        Compress-Archive -Path $items.FullName -DestinationPath '%ZIP_NAME%' -Force;
        $size = (Get-Item '%ZIP_NAME%').Length;
        Write-Host ('[成功] 源码包已生成: %ZIP_NAME%');
        Write-Host ('       大小: ' + [math]::Round($size/1MB, 2) + ' MB')
    } else {
        Write-Host '[错误] 没有可打包的文件'
    }
}"

echo.
echo 按任意键退出...
pause >nul
