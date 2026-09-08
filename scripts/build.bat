@echo off
REM 构建全部微服务 jar（使用项目自带便携 JDK17 + Maven + 阿里云镜像）
chcp 65001 >nul
set "JAVA_HOME=C:\Users\hongj\WorkBuddy\2026-09-08-02-20-00\tools\jdk-17.0.2"
set "MVN=C:\Users\hongj\WorkBuddy\2026-09-08-02-20-00\tools\apache-maven-3.9.16\bin\mvn.cmd"
set "SETTINGS=C:\Users\hongj\WorkBuddy\2026-09-08-02-20-00\tools\mvn-settings.xml"
set "BASE=%~dp0.."

echo 使用 JDK: %JAVA_HOME%
"%MVN%" -s "%SETTINGS%" -f "%BASE%\pom.xml" -DskipTests package
if %ERRORLEVEL% NEQ 0 (
  echo 构建失败，请检查上方错误信息。
  pause
  exit /b 1
)
echo ================================================
echo 构建成功！jar 位置:
echo   eureka-server\target\eureka-server-1.0.0.jar
echo   gateway-server\target\gateway-server-1.0.0.jar
echo   data-service\target\data-service-1.0.0.jar
echo   analytics-service\target\analytics-service-1.0.0.jar
echo 现在可运行 scripts\start-all.bat 启动平台
echo ================================================
pause
