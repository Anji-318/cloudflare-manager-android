@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
for %%i in ("%CD%") do set PROJECT_NAME=%%~ni
title %PROJECT_NAME% - Android智能编译脚本 v2.0

cd /d %~dp0

:: ========================================
:: 初始化检测（只执行一次）
:: ========================================
:INIT
echo ========================================
echo   %PROJECT_NAME% - Android智能编译脚本 v2.0
echo ========================================
echo.

:: 检查gradlew
if not exist ".\gradlew.bat" (
    echo [错误] 未找到gradlew.bat，请在项目根目录运行
    echo 按任意键继续...
    pause >nul
    exit /b 1
)

:: 检测模块
set MODULE_NAME=
if exist "app\build.gradle" set MODULE_NAME=app
if exist "app\build.gradle.kts" set MODULE_NAME=app
if "%MODULE_NAME%"=="" (
    echo [警告] 未检测到标准模块名，使用默认"app"
    set MODULE_NAME=app
)
echo [模块]: %MODULE_NAME%

:: 读取版本号（兼容 .gradle 和 .gradle.kts）
set VER=
if exist "%MODULE_NAME%\build.gradle.kts" (
    for /f "tokens=2 delims==" %%a in ('findstr "versionName" %MODULE_NAME%\build.gradle.kts') do (
        set VER=%%a
        set VER=!VER: =!
        set VER=!VER:"=!
    )
) else if exist "%MODULE_NAME%\build.gradle" (
    for /f "tokens=2 delims==" %%a in ('findstr "versionName" %MODULE_NAME%\build.gradle') do (
        set VER=%%a
        set VER=!VER: =!
        set VER=!VER:"=!
        set VER=!VER:'=!
    )
)
if not "!VER!"=="" echo [版本]: !VER!
echo.

:: ========================================
:: 主菜单
:: ========================================
:MAIN_MENU
echo ========================================
echo   [主菜单]
echo ========================================
echo   1 = 编译APK
echo   2 = 仅生成密钥明文 (.txt)
echo   3 = 仅生成签名密钥 (.jks)
echo   4 = 一键生成密钥 (txt + jks)
echo   5 = 帮助信息
echo   6 = 退出脚本
echo ========================================
choice /C 123456 /N /M "请选择:"
set MENU_CHOICE=%errorlevel%

if %MENU_CHOICE%==6 goto EXIT_SCRIPT
if %MENU_CHOICE%==5 goto SHOW_HELP
if %MENU_CHOICE%==4 goto GEN_BOTH
if %MENU_CHOICE%==3 goto GEN_JKS_MENU
if %MENU_CHOICE%==2 goto GEN_TXT_MENU
if %MENU_CHOICE%==1 goto COMPILE_MENU
echo [错误] 无效选择
echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 选项1: 编译APK (原始逻辑完整保留)
:: ========================================
:COMPILE_MENU
echo.
echo [守护进程]
echo   1 = 保留守护进程(更快)
echo   2 = 清理后编译(解决诡异错误)
choice /C 12 /N /M "请选择:"
if errorlevel 2 (
    echo 正在清理旧守护进程...
    call .\gradlew.bat --stop >nul 2>&1
    timeout /t 2 /nobreak >nul
    echo 已清理
)

echo.
echo [编译模式]
echo   1 = Debug   (极速开发)
echo   2 = Release (混淆+签名+防逆向)
echo   3 = 全部    (Debug+Release)
choice /C 123 /N /M "请选择:"

set BUILD_MODE=%errorlevel%

echo.
echo [开始编译]
echo   开始时间: %time%
echo.

set START_TIME=%time%

if %BUILD_MODE%==1 goto BUILD_DEBUG
if %BUILD_MODE%==2 goto BUILD_RELEASE
if %BUILD_MODE%==3 goto BUILD_BOTH

:BUILD_DEBUG
echo.
echo 编译Debug...
set APK_SEARCH_DIR=%MODULE_NAME%\build\outputs\apk\debug
if exist "%MODULE_NAME%\build\outputs\apk\debug\*.apk" del /q "%MODULE_NAME%\build\outputs\apk\debug\*.apk"
call .\gradlew.bat :%MODULE_NAME%:assembleDebug --parallel --build-cache --configure-on-demand -x lint -Dorg.gradle.jvmargs="-Xmx6g -XX:+UseParallelGC"
set GRADLE_EXIT=!errorlevel!
goto BUILD_DONE

:BUILD_RELEASE
echo.
echo 编译Release(ProGuard混淆+签名)...
echo [提示] Release编译时间较长，首次约5-10分钟...
set APK_SEARCH_DIR=%MODULE_NAME%\build\outputs\apk\release
if exist "%MODULE_NAME%\build\outputs\apk\release\*.apk" del /q "%MODULE_NAME%\build\outputs\apk\release\*.apk"
call .\gradlew.bat :%MODULE_NAME%:assembleRelease --parallel --build-cache --configure-on-demand -x lint -Dorg.gradle.jvmargs="-Xmx6g -XX:+UseParallelGC"
set GRADLE_EXIT=!errorlevel!
goto BUILD_DONE

:BUILD_BOTH
echo.
echo 编译Debug+Release...
set APK_SEARCH_DIR=%MODULE_NAME%\build\outputs\apk
call .\gradlew.bat :%MODULE_NAME%:assembleDebug :%MODULE_NAME%:assembleRelease --parallel --build-cache --configure-on-demand -x lint -Dorg.gradle.jvmargs="-Xmx6g -XX:+UseParallelGC"
set GRADLE_EXIT=!errorlevel!
goto BUILD_DONE

:BUILD_DONE
set END_TIME=%time%

if !GRADLE_EXIT! neq 0 (
    echo.
    echo [错误] 编译失败！错误码: !GRADLE_EXIT!
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

echo.
echo ========================================
echo 编译成功！
echo ========================================
echo.

set APK_COUNT=0
for /r "%APK_SEARCH_DIR%" %%f in (*.apk) do (
    if exist "%%f" (
        set /a APK_COUNT+=1
        echo   [!APK_COUNT!] %%~nxf
        echo       路径: %%f
    )
)

if !APK_COUNT!==0 (
    echo [警告] 未找到APK输出文件
)

echo.
echo   结束时间: %time%
echo.

echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 选项2: 仅生成密钥明文 (.txt)
:: ========================================
:GEN_TXT_MENU
echo.
echo ========================================
echo   生成明文密钥文件
echo ========================================
echo.
set /p KEY_NAME="请输入密钥基础名称(直接回车默认ScanTest-key): "
if "!KEY_NAME!"=="" set "KEY_NAME=ScanTest-key"
set "TXT_FILE=!KEY_NAME!.txt"
echo   输出文件: !TXT_FILE!
echo.
set "OVERWRITE=N"
if exist "!TXT_FILE!" (
    echo [提示] !TXT_FILE! 已存在，是否覆盖?
    set /p OVERWRITE="输入 Y 覆盖, 其他取消: "
)
if /I not "!OVERWRITE!"=="Y" if exist "!TXT_FILE!" (
    echo [取消] 操作已取消
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)
echo [生成] 正在创建32位随机密码...
call :GEN_PASSWORD
echo !STORE_PASS! > "!TXT_FILE!"
if exist "!TXT_FILE!" (
    echo [成功] 明文记录已保存: !TXT_FILE!
    echo   密码: !STORE_PASS!
) else (
    echo [错误] 文件写入失败
)
echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 选项3: 仅生成签名密钥 (.jks)
:: ========================================
:GEN_JKS_MENU
echo.
echo ========================================
echo   生成JKS签名密钥库
echo ========================================
echo.
set /p TXT_FILE="请输入密钥文件路径(直接回车默认ScanTest-key.txt): "
if "!TXT_FILE!"=="" set "TXT_FILE=ScanTest-key.txt"
if not exist "!TXT_FILE!" (
    echo [错误] 未找到 !TXT_FILE!
    echo [提示] 请先用选项2生成明文密钥文件
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)
echo [读取] 正在从 !TXT_FILE! 提取密码...
set /p JKS_PASS=<"!TXT_FILE!"
if "!JKS_PASS!"=="" (
    echo [错误] 密码文件为空
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)
echo [成功] 密码已读取

for %%i in ("!TXT_FILE!") do set "BASE_NAME=%%~ni"
set "JKS_FILE=!BASE_NAME!.jks"
echo   密钥库: !JKS_FILE!
echo   别名  : !BASE_NAME!

set "OVERWRITE_JKS=N"
if exist "!JKS_FILE!" (
    echo.
    echo [提示] !JKS_FILE! 已存在，是否覆盖?
    set /p OVERWRITE_JKS="输入 Y 覆盖, 其他保留现有: "
)
if /I not "!OVERWRITE_JKS!"=="Y" if exist "!JKS_FILE!" (
    echo [跳过] 保留现有 !JKS_FILE!
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

where keytool >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] 未找到 keytool，请确保 JDK 已安装
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

echo.
echo [生成] 正在创建 !JKS_FILE!...
keytool -genkeypair -v -keystore "!JKS_FILE!" -alias "!BASE_NAME!" -keyalg RSA -keysize 2048 -validity 10000 -storepass "!JKS_PASS!" -keypass "!JKS_PASS!" -dname "CN=ScanTest, OU=ScanTest, O=ScanTest, L=ScanTest, ST=ScanTest, C=CN" >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] JKS生成失败
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)
echo [成功] 签名密钥已生成: !JKS_FILE!
echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 选项4: 一键生成密钥 (txt + jks)
:: ========================================
:GEN_BOTH
echo.
echo ========================================
echo   一键生成 (txt + jks)
echo ========================================
echo.
set /p KEY_NAME="请输入密钥基础名称(直接回车默认ScanTest-key): "
if "!KEY_NAME!"=="" set "KEY_NAME=ScanTest-key"
set "TXT_FILE=!KEY_NAME!.txt"
set "JKS_FILE=!KEY_NAME!.jks"
echo   明文文件: !TXT_FILE!
echo   密钥库  : !JKS_FILE!
echo.

:: 检查txt
set "OVERWRITE=N"
if exist "!TXT_FILE!" (
    echo [提示] !TXT_FILE! 已存在，是否覆盖?
    set /p OVERWRITE="输入 Y 覆盖, 其他取消: "
)
if /I not "!OVERWRITE!"=="Y" if exist "!TXT_FILE!" (
    echo [取消] 操作已取消
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

echo [生成] 正在创建32位随机密码...
call :GEN_PASSWORD
echo !STORE_PASS! > "!TXT_FILE!"
if not exist "!TXT_FILE!" (
    echo [错误] 文件写入失败
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)
echo [成功] 明文记录已保存: !TXT_FILE!

:: 检查jks
set "OVERWRITE_JKS=N"
if exist "!JKS_FILE!" (
    echo.
    echo [提示] !JKS_FILE! 已存在，是否覆盖?
    set /p OVERWRITE_JKS="输入 Y 覆盖, 其他保留现有: "
)
if /I not "!OVERWRITE_JKS!"=="Y" if exist "!JKS_FILE!" (
    echo [跳过] 保留现有 !JKS_FILE!
    echo [完成] 明文记录: !TXT_FILE!
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

where keytool >nul 2>&1
if !errorlevel! neq 0 (
    echo.
    echo [警告] 未找到 keytool，仅生成明文记录
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

echo.
echo [生成] 正在创建JKS密钥库 !JKS_FILE!...
keytool -genkeypair -v -keystore "!JKS_FILE!" -alias "!KEY_NAME!" -keyalg RSA -keysize 2048 -validity 10000 -storepass "!STORE_PASS!" -keypass "!STORE_PASS!" -dname "CN=ScanTest, OU=ScanTest, O=ScanTest, L=ScanTest, ST=ScanTest, C=CN" >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] JKS生成失败
    echo 按任意键继续...
    pause >nul
    goto MAIN_MENU
)

echo.
echo ========================================
echo [全部完成]
echo ========================================
echo   明文记录: !TXT_FILE!
echo   密钥库  : !JKS_FILE!
echo   别名    : !KEY_NAME!
echo   密码    : !STORE_PASS!
echo ========================================
echo.
echo [重要提示]
echo   1. 请妥善保管 !TXT_FILE! 和 !JKS_FILE!
echo   2. 切勿将密钥文件上传至Git等公开仓库
echo   3. 建议将 *.jks 和 *-key.txt 加入 .gitignore
echo.
echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 选项5: 帮助信息
:: ========================================
:SHOW_HELP
cls
echo ========================================
echo   帮助信息
echo ========================================
echo.
echo [编译APK]
echo   1 = Debug   (极速开发，无签名)
echo   2 = Release (混淆+签名，需配置密钥)
echo   3 = 全部    (Debug+Release)
echo   编译完成后自动返回主菜单
echo.
echo [仅生成明文密钥 .txt]
echo   生成32位随机密码，保存为纯文本文件
echo   文件内容仅有一行密码，无其他信息
echo   默认文件名: ScanTest-key.txt
echo.
echo [仅生成签名密钥 .jks]
echo   读取已有的 .txt 文件中的密码
echo   生成对应的 .jks 签名密钥库
echo   需要 JDK 环境支持 (keytool)
echo.
echo [一键生成]
echo   同时生成 .txt 明文和 .jks 密钥库
echo   等同于依次执行选项2和选项3
echo.
echo [安全提示]
echo   - .jks 是签名核心，泄露后他人可伪造应用
echo   - .txt 仅用于本地备份密码
echo   - 建议将密钥文件加入 .gitignore
echo.
echo ========================================
echo 按任意键继续...
pause >nul
goto MAIN_MENU

:: ========================================
:: 退出脚本
:: ========================================
:EXIT_SCRIPT
echo.
echo [退出] 脚本已结束。
timeout /t 2 /nobreak >nul
exit /b 0

:: ========================================
:: 子程序: 生成32位随机密码
:: ========================================
:GEN_PASSWORD
set "CHARSET=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
set "STORE_PASS="
for /l %%i in (1,1,32) do (
    set /a "RND=!random! %% 62"
    for %%a in (!RND!) do (
        set "CH=!CHARSET:~%%a,1!"
        set "STORE_PASS=!STORE_PASS!!CH!"
    )
)
exit /b 0