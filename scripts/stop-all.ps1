# 旗鱼直播 - 一键停止全部 Java 服务（不动中间件）
# 策略（双保险）：
#   A. jps 按进程名匹配 qiyu 的 JVM，全部 kill（精准，不会误杀占用同端口的其他软件）
#   B. 兜底：扫描全部服务端口，仍在 LISTEN 的 PID 直接 kill（抓半死/非标准方式启动的残留进程）
# 中间件（MySQL/Redis/Nacos/RocketMQ/MinIO 容器）与 srs.exe 均不受影响。
# 用法: stop-all.bat   （或 powershell -File stop-all.ps1）

$ErrorActionPreference = "Continue"

# ---------- JDK17（与 start-all.ps1 一致：写死本机路径，可用 QIYU_JAVA_HOME 覆盖） ----------
$Jdk17 = if ($env:QIYU_JAVA_HOME) { $env:QIYU_JAVA_HOME } else { "D:\enviroment\javaenviroment\jdk17" }
$Jps = Join-Path $Jdk17 "bin\jps.exe"

# ---------- 全部自研服务端口（不含中间件，见 docs/ports.md） ----------
$ServicePorts = @(
  38080, 38085, 38090, 38095, 38100,                              # HTTP: 网关/api/SRS回调/支付回调/管理后台
  38110, 38115,                                                    # IM Netty TCP/WS
  30010, 30015, 30020, 30025, 30030, 30035, 30040, 30045, 30050,  # Dubbo providers
  30055, 30060, 30065
)

$targets = @{}   # pid -> 备注

# ---------- A. jps 进程名匹配 ----------
if (Test-Path $Jps) {
  & $Jps -l 2>$null | ForEach-Object {
    $line = $_.Trim()
    if ($line -match '^(\d+)\s+(.*)$') {
      $pid2 = $Matches[1]; $main = $Matches[2]
      if ($main -match 'qiyu') { $targets[$pid2] = "jps: $main" }
    }
  }
}
Write-Host "[A] jps 匹配到 $($targets.Count) 个 qiyu JVM"

# ---------- 执行 A 的 kill ----------
foreach ($p in @($targets.Keys)) {
  Write-Host "  kill $p ($($targets[$p]))"
  taskkill /PID $p /F 2>$null | Out-Null
  $targets.Remove($p) | Out-Null
  $targets[$p] = "killed"
}

Start-Sleep -Milliseconds 800

# ---------- B. 端口兜底扫描 ----------
$killedByPort = 0; $stillAlive = @()
foreach ($port in $ServicePorts) {
  $conn = netstat -ano 2>$null | Select-String "LISTENING" | Select-String ":$port\s"
  foreach ($c in $conn) {
    $ownerPid = ($c -split '\s+')[-1]
    if ($ownerPid -match '^\d+$' -and $ownerPid -ne '0') {
      Write-Host "  [端口兜底] :$port 被 PID $ownerPid 占用，kill"
      taskkill /PID $ownerPid /F 2>$null | Out-Null
      $killedByPort++
    }
  }
  # 复查
  $check = netstat -ano 2>$null | Select-String "LISTENING" | Select-String ":$port\s"
  if ($check) { $stillAlive += $port }
}

# ---------- 结果 ----------
Write-Host ""
if ($stillAlive.Count -eq 0) {
  Write-Host "[OK] 全部 Java 服务已停止（端口兜底清理 $killedByPort 个）" -ForegroundColor Green
} else {
  Write-Host "[WARN] 以下端口仍被占用: $($stillAlive -join ', ')" -ForegroundColor Yellow
  Write-Host "       可能是非 Java 进程占用，请人工确认: netstat -ano | findstr <端口>"
}
Write-Host "[INFO] 中间件容器(MySQL/Redis/Nacos/RocketMQ/MinIO)与 srs.exe 未做任何操作"
