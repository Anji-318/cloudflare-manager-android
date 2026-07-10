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
echo   包含: app/src, build.gradle.kts, settings.gradle.kts, gradle.properties, gradle/wrapper, README.md
echo.

set "PS_ZIP=!ZIP_NAME!"
powershell -NoProfile -ExecutionPolicy Bypass -Command "& {Add-Type -AssemblyName System.IO.Compression.FileSystem; $zip = '%PS_ZIP%'; $sw = [System.IO.Compression.ZipFile]::Open($zip, 'Create'); $baseLen = (Get-Location).Path.Length + 1; $entries = @(); function Add-Files($path, $exclude) { $items = Get-ChildItem $path; foreach ($item in $items) { $skip = $false; foreach ($e in $exclude) { if ($item.Name -like $e) { $skip = $true; break } } if ($skip) { continue }; $rel = $item.FullName.Substring($baseLen).Replace('\', '/'); if ($item.PSIsContainer) { Add-Files $item.FullName $exclude } else { $entries += $rel; $sw.CreateEntryFromFile($item.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null } } }; Add-Files 'app' @('build', '.externalNativeBuild', '.cxx', 'release'); Add-Files 'gradle' @(); $files = @('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'README.md'); foreach ($f in $files) { if (Test-Path $f) { $sw.CreateEntryFromFile($f, (Split-Path $f -Leaf), [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null } }; $sw.Dispose(); $size = (Get-Item $zip).Length; Write-Host ('[成功] 源码包已生成: ' + $zip); Write-Host ('       大小: ' + [math]::Round($size/1KB, 2) + ' KB')}"

echo.
echo 按任意键退出...
pause >nul
