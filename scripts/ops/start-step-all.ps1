#requires -Version 5.1
param(
  [switch]$SkipInfra,
  [int]$StartupWait = 15,
  [int]$NacosTimeout = 60
)

$ErrorActionPreference = "Continue"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$LogDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

$JavaOpts  = if ($env:JAVA_OPTS)  { $env:JAVA_OPTS }  else { "-Xms128m -Xmx384m" }
$NacosAddr = if ($env:NACOS_ADDR) { $env:NACOS_ADDR } else { "127.0.0.1:8848" }

$Jdk17  = if ($env:QIYU_JAVA_HOME) { $env:QIYU_JAVA_HOME } else { "D:\enviroment\javaenviroment\jdk17" }
$JavaExe = Join-Path $Jdk17 "bin\java.exe"
$JpsExe  = Join-Path $Jdk17 "bin\jps.exe"

if (-not (Test-Path $JavaExe)) {
  Write-Host "[FAIL] JDK 17 not found at: $Jdk17" -ForegroundColor Red
  exit 1
}
$env:JAVA_HOME = $Jdk17

# Dubbo 注册 IP 统一固定：默认用真实网卡 IP，避免注册到 VMware 等虚拟网卡导致跨机不可达
# （可用环境变量 DUBBO_IP_TO_REGISTRY 覆盖）
$env:DUBBO_IP_TO_REGISTRY = if ($env:DUBBO_IP_TO_REGISTRY) { $env:DUBBO_IP_TO_REGISTRY } else { "192.168.31.252" }
$env:Path = "$Jdk17\bin;" + $env:Path

$InfraPorts = @{ "MySQL" = 3306; "Redis" = 6379; "Nacos" = 8848; "RocketMQ" = 9876 }
$NacosNamespaceId = "ef63e53e-94c8-4b1c-865e-6824177b2893"
$NacosUser = "nacos"
$NacosPass = "nacos"
$NacosToken = $null

function Get-Nacos-Token {
  try {
    $loginResp = Invoke-RestMethod -Method Post -Uri "http://$NacosAddr/nacos/v1/auth/login" -Body @{ username = $NacosUser; password = $NacosPass } -TimeoutSec 5 -ErrorAction Stop
    $script:NacosToken = $loginResp.accessToken
    Write-Host "[NACOS] logged in, token expires in $($loginResp.tokenTtl)s" -ForegroundColor DarkGray
  } catch {
    Write-Host "[WARN] Nacos login failed, using anonymous access: $_" -ForegroundColor Yellow
  }
}

$ModuleExtraEnv = @{
  # Dubbo 3.2 rejects 127.0.0.1 as DUBBO_IP_TO_REGISTRY ("Specified invalid registry ip")
  # and the whole Dubbo module then fails to start (IRouterHandlerRpc never exported).
  # Use the real NIC IP: the Netty starters reuse the same var for their Redis
  # bind address, so Dubbo registration and IM routing stay consistent.
  "qiyu-live-im-core-server" = @{ "DUBBO_PORT_TO_REGISTRY" = "30035" }
}

# Startup-verify exemptions: the verifier force-checks every @DubboReference
# (check=true), but user<->living and living<->stream reference each other in code
# (a dependency cycle). The weak edges below must be exempted by interface name,
# otherwise no start order can pass verification. Exemption affects startup check
# only; references still lazy-load at runtime.
$ModuleExtraArgs = @{
  "qiyu-live-user-provider"   = "--qiyu.startup.ignore-references=ILivingRoomRpc"
  "qiyu-live-living-provider" = "--qiyu.startup.ignore-references=ILivingStreamRpc"
}

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

function Test-Port($port) {
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $r = $c.BeginConnect("127.0.0.1", $port, $null, $null)
    return $r.AsyncWaitHandle.WaitOne(1500) -and $c.Connected
  } catch { return $false } finally { $c.Close() }
}

function Wait-For-Nacos($serviceName) {
  Write-Host "    waiting for $serviceName to register in Nacos ..." -ForegroundColor DarkGray
  for ($i = 0; $i -lt $NacosTimeout; $i++) {
    Start-Sleep -Seconds 1
    try {
      $headers = @{}
      if ($NacosToken) { $headers["Authorization"] = "Bearer $NacosToken" }
      $resp = Invoke-RestMethod -Uri "http://$NacosAddr/nacos/v1/ns/instance/list" `
        -Headers $headers `
        -Body @{ serviceName = $serviceName; namespaceId = $NacosNamespaceId } `
        -TimeoutSec 3 -ErrorAction SilentlyContinue
      if ($resp.hosts.Count -gt 0) {
        Write-Host "    [OK] $serviceName registered ($($resp.hosts.Count) host(s))" -ForegroundColor Green
        return $true
      }
    } catch { }
  }
  Write-Host "    [WARN] $serviceName not registered after ${NacosTimeout}s, continuing ..." -ForegroundColor Yellow
  return $false
}

function Start-Svc($mod) {
  $jar = Find-Jar $mod
  if (-not $jar) {
    Write-Host "  [X] $mod : no jar found" -ForegroundColor Red
    return $false
  }
  if (Test-Running $mod) {
    $pidVal = Get-Content (Join-Path $LogDir "$mod.pid")
    Write-Host "  [=] $mod already running (PID $pidVal)" -ForegroundColor DarkGray
    Wait-For-Nacos $mod
    return $true
  }
  Write-Host "  [>] starting $mod ..." -ForegroundColor Cyan
  $out = Join-Path $LogDir "$mod.log"
  $err = Join-Path $LogDir "$mod.err.log"
  $extraArgs = if ($ModuleExtraArgs.ContainsKey($mod)) { " " + $ModuleExtraArgs[$mod] } else { "" }
  $argStr = "$JavaOpts -Dfile.encoding=UTF-8 -jar `"$($jar.FullName)`"$extraArgs"

  if ($ModuleExtraEnv.ContainsKey($mod)) {
    foreach ($k in $ModuleExtraEnv[$mod].Keys) {
      Set-Item -Path ("env:" + $k) -Value $ModuleExtraEnv[$mod][$k]
    }
  }

  $p = Start-Process -FilePath $JavaExe -ArgumentList $argStr -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden

  if ($ModuleExtraEnv.ContainsKey($mod)) {
    foreach ($k in $ModuleExtraEnv[$mod].Keys) {
      Remove-Item -Path ("env:" + $k) -ErrorAction SilentlyContinue
    }
  }

  Set-Content -Path (Join-Path $LogDir "$mod.pid") -Value $p.Id
  Write-Host "    PID: $($p.Id), sleep ${StartupWait}s for boot ..." -ForegroundColor DarkGray
  Start-Sleep -Seconds $StartupWait
  Wait-For-Nacos $mod
  return $true
}

function Stop-All {
  Write-Host "[STOP] cleaning up old instances ..." -ForegroundColor Yellow
  if (Test-Path $JpsExe) {
    $pids = (& $JpsExe -l 2>$null | Select-String -Pattern "qiyu-live" | ForEach-Object { ($_ -split '\s+')[0] })
    foreach ($p in $pids) {
      if ($p -match '^\d+$') {
        try { Stop-Process -Id ([int]$p) -Force -ErrorAction SilentlyContinue } catch { }
      }
    }
  }
  Start-Sleep -Seconds 1
}

function Ensure-Infra {
  if ($SkipInfra) { return }
  $allUp = $true
  foreach ($port in $InfraPorts.Values) {
    if (-not (Test-Port $port)) { $allUp = $false; break }
  }
  if ($allUp) {
    Write-Host "[INFRA] all middleware ports already ready" -ForegroundColor Green
    return
  }
  Write-Host "[INFRA] middleware not ready, running docker compose up -d ..." -ForegroundColor Cyan
  docker compose -f (Join-Path $ProjectRoot "docker-compose.yml") up -d
  if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] docker compose failed. Is Docker Desktop running?" -ForegroundColor Red
    exit 1
  }
  foreach ($name in $InfraPorts.Keys) {
    $port = $InfraPorts[$name]
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-Port $port) { $ok = $true; break }
      Start-Sleep -Seconds 2
    }
    if ($ok) { Write-Host "  [OK] $name ($port) ready" -ForegroundColor Green }
    else     { Write-Host "  [X] $name ($port) still unreachable" -ForegroundColor Red }
  }
}

# ---------- service list in strict dependency order ----------
# stream-provider references living + im-router (ImBroadcastServiceImpl), so it
# MUST start after both. user/living/stream carry exemption args (see
# $ModuleExtraArgs) due to their mutual-reference cycle.
$Services = @(
  "qiyu-live-bank-provider",
  "qiyu-live-account-provider",
  "qiyu-live-id-generate-provider",
  "qiyu-live-im-provider",
  "qiyu-live-im-core-server",
  "qiyu-live-im-router-provider",
  "qiyu-live-user-provider",
  "qiyu-live-living-provider",
  "qiyu-live-stream-provider",
  "qiyu-live-msg-provider",
  "qiyu-live-gift-provider",
  "qiyu-live-video-provider",
  "qiyu-live-gateway",
  "qiyu-live-bank-api",
  "qiyu-live-api",
  "qiyu-live-admin-api"
)

# ---------- main ----------
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   Qiyu Live - Step-by-Step Start (Strict Order)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

Get-Nacos-Token
Ensure-Infra

$started = @()
foreach ($svc in $Services) {
  Write-Host ""
  Write-Host "--> $svc" -ForegroundColor Cyan
  $ok = Start-Svc $svc
  if (-not $ok) {
    Write-Host "[WARN] $svc failed, continuing with next ..." -ForegroundColor Yellow
  }
  $started += $svc
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "   Done: $($started.Count)/$($Services.Count) services started" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  status : powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 status"
Write-Host "  logs   : powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 logs <module>"
Write-Host "  web    : scripts\start-web.bat"
Write-Host "  stop   : powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 stop"
