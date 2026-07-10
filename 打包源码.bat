@echo off
chcp 65001 >nul

for %%i in ("%CD%") do set PROJECT_NAME=%%~ni
set "ZIP_NAME=%PROJECT_NAME%-v{VER}-src.zip"

echo ========================================
echo   %PROJECT_NAME% 源码打包
echo ========================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "& {$projName = '%PROJECT_NAME%'; $zipName = $projName + '-v'; $ver = 'unknown'; try { $content = Get-Content 'app\build.gradle.kts' -Raw; if ($content -match 'versionName\s*=\s*\"([^\"]+)\"') { $ver = $matches[1] } } catch {}; $zipName = $projName + '-v' + $ver + '-src.zip'; Write-Host ('版本: ' + $ver); Write-Host ''; Write-Host ('[打包] 正在生成 ' + $zipName + ' ...'); if (Test-Path $zipName) { Remove-Item $zipName -Force }; $items = @('app', 'gradle', '.github', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat', 'README.md', 'Android智能编译脚本_v2.0.bat', '打包源码.bat'); Compress-Archive -Path $items -DestinationPath $zipName -Force; $size = (Get-Item $zipName).Length; Write-Host ''; Write-Host ('[成功] 源码包已生成: ' + $zipName); Write-Host ('       大小: ' + [math]::Round($size/1KB, 2) + ' KB')}"

echo.
echo 按任意键退出...
pause >nul
