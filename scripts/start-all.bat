@echo off
REM 智辉电商全域运营数据分析平台 - 一键启动全部服务
REM 用法: 双击运行，或在 cmd 中执行 start-all.bat
chcp 65001 >nul
setlocal

set JAVA_EXE=%~dp0..\..\tools\jdk-17.0.2\bin\java.exe
set BASE=%~dp0..

echo ================================================
echo  智辉电商全域运营数据分析平台 - 启动中...
echo  注册中心 http://localhost:8761
echo  网关     http://localhost:19080
echo  大屏     http://localhost:5173
echo ================================================

echo [1/6] 启动注册中心 eureka-server ...
start "zhihui-eureka" "%JAVA_EXE%" -Xms128m -Xmx256m -jar "%BASE%\eureka-server\target\eureka-server-1.0.0.jar"

echo      等待注册中心就绪 (25s) ...
timeout /t 25 /nobreak >nul

echo [2/6] 启动认证服务 auth-service ...
start "zhihui-auth" "%JAVA_EXE%" -Xms128m -Xmx320m -jar "%BASE%\auth-service\target\auth-service-1.0.0.jar"

echo [3/6] 启动 API 网关 gateway-server ...
start "zhihui-gateway" "%JAVA_EXE%" -Xms128m -Xmx320m -jar "%BASE%\gateway-server\target\gateway-server-1.0.0.jar"

echo [4/6] 启动数据采集服务 data-service (首次启动将生成模拟数据，约 1-2 分钟) ...
start "zhihui-data" "%JAVA_EXE%" -Xms128m -Xmx512m -jar "%BASE%\data-service\target\data-service-1.0.0.jar"

echo [5/6] 启动数据分析服务 analytics-service ...
start "zhihui-analytics" "%JAVA_EXE%" -Xms128m -Xmx512m -jar "%BASE%\analytics-service\target\analytics-service-1.0.0.jar"

echo      等待服务就绪 (20s) ...
timeout /t 20 /nobreak >nul

echo [6/6] 启动前端大屏 (Vite dev server) ...
start "zhihui-frontend" cmd /k "cd /d %BASE%\frontend && npm run dev"

echo ================================================
echo  全部服务已拉起！
echo  注册中心: http://localhost:8761
echo  网关:     http://localhost:19080
echo  数据大屏: http://localhost:5173  (浏览器打开)
echo  关闭平台请运行 stop-all.bat
echo ================================================
endlocal
