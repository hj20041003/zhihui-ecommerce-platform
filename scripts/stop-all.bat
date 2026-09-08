@echo off
REM 停止智辉平台全部 Java 服务与前端 dev server（按命令行匹配本项目路径）
chcp 65001 >nul
echo 正在停止智辉电商平台相关进程 ...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*zhihui-ecommerce-platform*' } | ForEach-Object { Write-Host ('停止 PID ' + $_.ProcessId + ' : ' + $_.Name); Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
echo 完成。
pause
