#requires -Version 5.1
<#
.SYNOPSIS
  Qiyu Live Platform - 单模块重新部署脚本 (Stop -> Rebuild -> Start)
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\reload-single.ps1 qiyu-live-api
  powershell -ExecutionPolicy Bypass -File scripts\reload-single.ps1 api
  powershell -ExecutionPolicy Bypass -File scripts\reload-single.ps1 qiyu-live-living-provider -SkipBuild
  powershell -ExecutionPolicy Bypass -File scripts\reload-single.ps1 qiyu-live-user-provider -Follow
.NOTES
  Java 环境、JVM 参数与启动规范与 scripts\start-all.ps1 严格保持一致
#>
param(
  [Parameter(Position=0)]
  [string]$ModuleName,

  [switch]$SkipBuild,
  [switch]$Follow
)

$ErrorActionPreference = "Continue"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$LogDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

$JavaOpts    = if ($env:JAVA_OPTS)    { $env:JAVA_OPTS }    else { "-Xms128m -Xmx384m" }
$NacosAddr   = if ($env:NACOS_ADDR)   { $env:NACOS_ADDR }   else { "127.0.0.1:8848" }

# ---------- JDK 17 resolution (与 start-all.ps1 保持一致) ----------
$Jdk17 = if ($env:QIYU_JAVA_HOME) { $env:QIYU_JAVA_HOME } else { "D:\enviroment\javaenviroment\jdk17" }
$JavaExe = Join-Path $Jdk17 "bin\java.exe"
$JpsExe  = Join-Path $Jdk17 "bin\jps.exe"

if (-not (Test-Path $JavaExe)) {
  Write-Host "[FAIL] JDK 17 not found at: $Jdk17" -ForegroundColor Red
  Write-Host "       set QIYU_JAVA_HOME to your JDK17 path, e.g.:" -ForegroundColor Red
  Write-Host '       $env:QIYU_JAVA_HOME = "D:\enviroment\javaenviroment\jdk17"' -ForegroundColor Red
  exit 1
}
$env:JAVA_HOME = $Jdk17

# Dubbo 注册 IP 统一固定：默认用真实网卡 IP，避免注册到 VMware 等虚拟网卡导致跨机不可达
# （可用环境变量 DUBBO_IP_TO_REGISTRY 覆盖）
$env:DUBBO_IP_TO_REGISTRY = if ($env:DUBBO_IP_TO_REGISTRY) { $env:DUBBO_IP_TO_REGISTRY } else { "192.168.31.252" }
$env:Path = "$Jdk17\bin;" + $env:Path
Write-Host "[JAVA] using JDK: $Jdk17" -ForegroundColor DarkGray

# ---------- 特殊模块环境变量配置 (与 start-all.ps1 保持一致) ----------
# im-core-server 启动需要注册 IP 与端口到 Redis
$ModuleExtraEnv = @{
  # Dubbo 3.2 拒绝把 127.0.0.1 注册到 Nacos（与 start-all.ps1 保持一致，用真实网卡 IP）
  "qiyu-live-im-core-server" = @{ "DUBBO_PORT_TO_REGISTRY" = "30035" }
}

# Startup-verify exemptions（与 start-all.ps1 保持一致）：user<->living、living<->stream 循环依赖的弱边豁免
$ModuleExtraArgs = @{
  "qiyu-live-user-provider"   = "--qiyu.startup.ignore-references=ILivingRoomRpc"
  "qiyu-live-living-provider" = "--qiyu.startup.ignore-references=ILivingStreamRpc"
}

$AllKnownModules = @(
  "qiyu-live-bank-provider",
  "qiyu-live-account-provider",
  "qiyu-live-id-generate-provider",
  "qiyu-live-im-provider",
  "qiyu-live-stream-provider",
  "qiyu-live-user-provider",
  "qiyu-live-im-core-server",
  "qiyu-live-gateway",
  "qiyu-live-bank-api",
  "qiyu-live-im-router-provider",
  "qiyu-live-living-provider",
  "qiyu-live-msg-provider",
  "qiyu-live-gift-provider",
  "qiyu-live-video-provider",
  "qiyu-live-api",
  "qiyu-live-admin-api"
)

# ---------- 模块名称交互与自动补全 ----------
if ([string]::IsNullOrWhiteSpace($ModuleName)) {
  Write-Host "`n=== Qiyu Live - Reload / Redeploy Single Module ===" -ForegroundColor Cyan
  Write-Host "Available modules:"
  for ($i = 0; $i -lt $AllKnownModules.Count; $i++) {
    Write-Host ("  [{0,2}] {1}" -f ($i + 1), $AllKnownModules[$i])
  }
  $inputVal = Read-Host "`nPlease enter module name or index"
  if ([string]::IsNullOrWhiteSpace($inputVal)) {
    Write-Host "[FAIL] No module name provided" -ForegroundColor Red
    exit 1
  }
  $idx = 0
  if ([int]::TryParse($inputVal, [ref]$idx) -and $idx -ge 1 -and $idx -le $AllKnownModules.Count) {
    $ModuleName = $AllKnownModules[$idx - 1]
  } else {
    $ModuleName = $inputVal.Trim()
  }
}

# 简写智能匹配（如输入 api -> 匹配 qiyu-live-api）
if (-not (Test-Path (Join-Path $ProjectRoot $ModuleName))) {
  if (Test-Path (Join-Path $ProjectRoot "qiyu-live-$ModuleName")) {
    $ModuleName = "qiyu-live-$ModuleName"
  }
}

$modulePath = Join-Path $ProjectRoot $ModuleName
if (-not (Test-Path $modulePath)) {
  Write-Host "[FAIL] Module directory not found: $modulePath" -ForegroundColor Red
  exit 1
}

# ---------- 辅助函数 ----------
function Find-Jar($mod) {
  $dir = Join-Path $ProjectRoot $mod
  $j = Get-ChildItem -Path (Join-Path $dir "target\*.jar") -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(-sources\.jar|-javadoc\.jar|\.jar\.original)$' }
  return ($j | Select-Object -First 1)
}

function Test-Running($mod) {
  $f = Join-Path $LogDir "$mod.pid"
  if (Test-Path $f) {
    $raw = Get-Content $f -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) { return $false }
    $pidVal = 0
    if (-not [int]::TryParse($raw.Trim(), [ref]$pidVal)) { return $false }
    try {
      $proc = Get-Process -Id $pidVal -ErrorAction Stop
      if ($proc.ProcessName -in @("java", "javaw")) { return $true }
      return $false
    } catch { return $false }
  }
  return $false
}

# ---------- 步骤 1: 停止旧进程 ----------
Write-Host "`n===== Step 1: Stopping $ModuleName =====" -ForegroundColor Cyan
$stopped = $false

# 1.1 优先检查 pid 文件
$pidFile = Join-Path $LogDir "$ModuleName.pid"
if (Test-Path $pidFile) {
  $raw = Get-Content $pidFile -Raw
  $pidVal = 0
  if ([int]::TryParse($raw.Trim(), [ref]$pidVal)) {
    try {
      $proc = Get-Process -Id $pidVal -ErrorAction Stop
      if ($proc.ProcessName -in @("java", "javaw")) {
        Write-Host "  Found pid file (PID: $pidVal), stopping process..."
        Stop-Process -Id $pidVal -Force -ErrorAction Stop
        $stopped = $true
      }
    } catch {
      Write-Host "  Process from pid file already exited (PID: $pidVal)" -ForegroundColor DarkGray
    }
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

# 1.2 兜底使用 jps 清理可能残留的进程
if (Test-Path $JpsExe) {
  try {
    $jpsLines = & $JpsExe -l 2>$null | Select-String -Pattern $ModuleName
    foreach ($line in $jpsLines) {
      $jpsPid = ($line.ToString().Trim() -split '\s+')[0]
      if ($jpsPid -match '^\d+$') {
        Write-Host "  [jps] Found matching JVM (PID: $jpsPid), stopping..." -ForegroundColor Yellow
        try {
          Stop-Process -Id ([int]$jpsPid) -Force -ErrorAction SilentlyContinue
          $stopped = $true
        } catch {}
      }
    }
  } catch {}
}

if ($stopped) {
  Start-Sleep -Seconds 1
  Write-Host "  [OK] Old process stopped" -ForegroundColor Green
} else {
  Write-Host "  [-] Module is not currently running" -ForegroundColor DarkGray
}

# ---------- 步骤 2: 重新编译打包 ----------
if ($SkipBuild) {
  Write-Host "`n===== Step 2: Skip build (-SkipBuild) =====" -ForegroundColor DarkGray
} else {
  Write-Host "`n===== Step 2: Building $ModuleName =====" -ForegroundColor Cyan
  Write-Host "Command: mvn -pl $ModuleName -am clean package -DskipTests" -ForegroundColor DarkGray
  Push-Location $ProjectRoot
  try {
    mvn -pl $ModuleName -am clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
      Write-Host "`n[FAIL] Maven build failed, aborting deployment!" -ForegroundColor Red
      exit 1
    }
  } finally {
    Pop-Location
  }
  Write-Host "  [OK] Maven build completed" -ForegroundColor Green
}

# ---------- 步骤 3: 启动新实例 ----------
Write-Host "`n===== Step 3: Starting $ModuleName =====" -ForegroundColor Cyan
$jar = Find-Jar $ModuleName
if (-not $jar) {
  Write-Host "[FAIL] No jar found (target\*.jar) for $ModuleName" -ForegroundColor Red
  exit 1
}

$out = Join-Path $LogDir "$ModuleName.log"
$err = Join-Path $LogDir "$ModuleName.err.log"
$extraArgs = if ($ModuleExtraArgs.ContainsKey($ModuleName)) { " " + $ModuleExtraArgs[$ModuleName] } else { "" }
$argStr = "$JavaOpts -Dfile.encoding=UTF-8 -jar `"$($jar.FullName)`"$extraArgs"

# 注入特殊环境变量
if ($ModuleExtraEnv.ContainsKey($ModuleName)) {
  foreach ($k in $ModuleExtraEnv[$ModuleName].Keys) {
    Set-Item -Path ("env:" + $k) -Value $ModuleExtraEnv[$ModuleName][$k]
  }
}

$p = Start-Process -FilePath $JavaExe -ArgumentList $argStr -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden

if ($ModuleExtraEnv.ContainsKey($ModuleName)) {
  foreach ($k in $ModuleExtraEnv[$ModuleName].Keys) {
    Remove-Item -Path ("env:" + $k) -ErrorAction SilentlyContinue
  }
}

Set-Content -Path (Join-Path $LogDir "$ModuleName.pid") -Value $p.Id

# 等待 2 秒自检进程状态
Start-Sleep -Seconds 2
if (Test-Running $ModuleName) {
  Write-Host "`n[OK] $ModuleName deployed and started successfully! (New PID: $($p.Id))" -ForegroundColor Green
  Write-Host "     Watch log: powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 logs $ModuleName`n" -ForegroundColor Cyan
} else {
  Write-Host "`n[FAIL] $ModuleName failed to start or exited unexpectedly!" -ForegroundColor Red
  if (Test-Path $err) {
    Write-Host "--- Error Log ($err) ---" -ForegroundColor Red
    Get-Content $err -Tail 15
  }
  if (Test-Path $out) {
    Write-Host "--- Standard Log ($out) ---" -ForegroundColor DarkGray
    Get-Content $out -Tail 15
  }
  exit 1
}

if ($Follow) {
  Write-Host "=== Watching $ModuleName log (Ctrl+C to exit) ===" -ForegroundColor Cyan
  Get-Content $out -Tail 30 -Wait
}
