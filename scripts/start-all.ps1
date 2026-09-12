#requires -Version 5.1
<#
.SYNOPSIS
  Qiyu Live Platform - one-click start/stop (Windows PowerShell)
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1           # build (if needed) + start all
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 infra     # docker compose middleware + nacos namespace
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 build     # mvn clean install -DskipTests
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 start     # start all (wave order)
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 stop
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 restart
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 status
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 logs qiyu-live-api
  powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 web       # start vite dev server (web_live)
.NOTES
  Optional env: JAVA_OPTS / QIYU_JAVA_HOME / NACOS_ADDR / STARTUP_WAIT
#>
param(
  [string]$Action = "start",
  [string]$LogModule = ""
)
$ErrorActionPreference = "Continue"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$LogDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

$JavaOpts    = if ($env:JAVA_OPTS)    { $env:JAVA_OPTS }    else { "-Xms128m -Xmx384m" }
$NacosAddr   = if ($env:NACOS_ADDR)   { $env:NACOS_ADDR }   else { "127.0.0.1:8848" }
$StartupWait = if ($env:STARTUP_WAIT) { [int]$env:STARTUP_WAIT } else { 10 }

# ---------- JDK 17 resolution (project requires Java 17; system default java may be 8) ----------
$Jdk17 = if ($env:QIYU_JAVA_HOME) { $env:QIYU_JAVA_HOME } else { "D:\enviroment\javaenviroment\jdk17" }
$JavaExe = Join-Path $Jdk17 "bin\java.exe"
if (-not (Test-Path $JavaExe)) {
  Write-Host "[FAIL] JDK 17 not found at: $Jdk17" -ForegroundColor Red
  Write-Host "       set QIYU_JAVA_HOME to your JDK17 path, e.g.:" -ForegroundColor Red
  Write-Host '       $env:QIYU_JAVA_HOME = "D:\enviroment\javaenviroment\jdk17"' -ForegroundColor Red
  exit 1
}
$env:JAVA_HOME = $Jdk17
$env:Path = "$Jdk17\bin;" + $env:Path
Write-Host "[JAVA] using JDK: $Jdk17" -ForegroundColor DarkGray

# ---------- middleware ports must match docker-compose.yml / application.yml ----------
$InfraPorts = @{ "MySQL" = 3306; "Redis" = 6379; "Nacos" = 8848; "RocketMQ" = 9876 }
$NacosNamespaceId = "ef63e53e-94c8-4b1c-865e-6824177b2893"
$NacosNamespaceName = "qiyu-live-test"

# Dependency layers (bottom first). Comment out any module you do not need.
$Waves = @(
  @{ Name="Wave0 base (no Dubbo dep)"; Mods=@("qiyu-live-bank-provider","qiyu-live-account-provider","qiyu-live-id-generate-provider","qiyu-live-im-provider","qiyu-live-stream-provider") },
  @{ Name="Wave1 depends on base";     Mods=@("qiyu-live-user-provider","qiyu-live-im-core-server","qiyu-live-gateway","qiyu-live-bank-api") },
  @{ Name="Wave2 IM router";            Mods=@("qiyu-live-im-router-provider") },
  @{ Name="Wave3 living room";          Mods=@("qiyu-live-living-provider") },
  @{ Name="Wave4 msg / gift";           Mods=@("qiyu-live-msg-provider","qiyu-live-gift-provider") },
  @{ Name="Wave5 main API";             Mods=@("qiyu-live-api") }
)
$AllModules = $Waves.ForEach{ $_.Mods }

# im-core-server reads these env vars at startup (Netty server identity in Redis), fails without them
$ModuleExtraEnv = @{
  "qiyu-live-im-core-server" = @{ "DUBBO_IP_TO_REGISTRY" = "127.0.0.1"; "DUBBO_PORT_TO_REGISTRY" = "30007" }
}

# ---------- helpers ----------
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
      # pid 可能被其他程序复用，只有 java 进程才算本服务在运行
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
function Build-All {
  Write-Host "[BUILD] mvn clean install -DskipTests ..." -ForegroundColor Cyan
  mvn clean install -DskipTests
  if ($LASTEXITCODE -ne 0) { Write-Host "[FAIL] build failed" -ForegroundColor Red; exit 1 }
  Write-Host "[OK] build done" -ForegroundColor Green
}
function Start-Module($mod) {
  $jar = Find-Jar $mod
  if (-not $jar) {
    Write-Host ("  [X] {0}: no jar found (target\*.jar). Run: scripts\start-all.ps1 build" -f $mod) -ForegroundColor Red
    return $false
  }
  if (Test-Running $mod) {
    $pidVal = Get-Content (Join-Path $LogDir "$mod.pid")
    Write-Host ("  [=] {0}: already running (pid {1}), skip" -f $mod, $pidVal)
    return $true
  }
  Write-Host ("  [>] {0} ..." -f $mod)
  $out = Join-Path $LogDir "$mod.log"
  $err = Join-Path $LogDir "$mod.err.log"
  $argStr = "$JavaOpts -Dfile.encoding=UTF-8 -jar `"$($jar.FullName)`""
  foreach ($k in $ModuleExtraEnv[$mod].Keys) { Set-Item -Path ("env:" + $k) -Value $ModuleExtraEnv[$mod][$k] }
  $p = Start-Process -FilePath $JavaExe -ArgumentList $argStr -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
  foreach ($k in $ModuleExtraEnv[$mod].Keys) { Remove-Item -Path ("env:" + $k) -ErrorAction SilentlyContinue }
  Set-Content -Path (Join-Path $LogDir "$mod.pid") -Value $p.Id
  return $true
}
function Start-Wave($w) {
  Write-Host ("-- {0} --" -f $w.Name) -ForegroundColor Cyan
  foreach ($mod in $w.Mods) { [void](Start-Module $mod) }
  Start-Sleep -Seconds $StartupWait
}
function Ensure-NacosNamespace {
  try {
    $login = Invoke-RestMethod -Method Post -Uri "http://$NacosAddr/nacos/v1/auth/login" -Body @{ username = "nacos"; password = "nacos" } -TimeoutSec 5
    $token = $login.accessToken
    $r = Invoke-WebRequest -Method Post -Uri "http://$NacosAddr/nacos/v1/console/namespaces" -Body @{ customNamespaceId = $NacosNamespaceId; namespaceName = $NacosNamespaceName; namespaceDesc = "qiyu local"; accessToken = $token } -TimeoutSec 5 -UseBasicParsing
    Write-Host "[OK] nacos namespace created: $NacosNamespaceName" -ForegroundColor Green
  } catch {
    # namespace already exists or nacos auth disabled - both fine
    Write-Host "[OK] nacos namespace ready (or already exists)" -ForegroundColor DarkGray
  }
}
function Start-Infra {
  # skip docker compose when every middleware port is already reachable
  $allUp = $true
  foreach ($port in $InfraPorts.Values) { if (-not (Test-Port $port)) { $allUp = $false; break } }
  if ($allUp) {
    Write-Host "[INFRA] all middleware ports already up, skip docker compose" -ForegroundColor Green
    Ensure-NacosNamespace
    return
  }
  Write-Host "[INFRA] docker compose up -d (mysql/redis/nacos/rocketmq/srs/minio) ..." -ForegroundColor Cyan
  docker compose -f (Join-Path $ProjectRoot "docker-compose.yml") up -d
  if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] docker compose failed. Is Docker Desktop running?" -ForegroundColor Red
    exit 1
  }
  Write-Host "[INFRA] waiting for middleware ports to be ready (up to 120s) ..." -ForegroundColor Cyan
  foreach ($name in $InfraPorts.Keys) {
    $port = $InfraPorts[$name]
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-Port $port) { $ok = $true; break }
      Start-Sleep -Seconds 2
    }
    if ($ok) { Write-Host ("  [OK] {0} (port {1})" -f $name, $port) -ForegroundColor Green }
    else     { Write-Host ("  [X] {0} (port {1}) NOT reachable" -f $name, $port) -ForegroundColor Red }
  }
  Ensure-NacosNamespace
  Write-Host "[INFRA] done. Note: MySQL auto-runs sql/*.sql on FIRST start only." -ForegroundColor Green
}
function Start-All {
  Start-Infra
  $need = $false
  foreach ($mod in $AllModules) { if (-not (Find-Jar $mod)) { $need = $true; break } }
  if ($need) { Build-All }
  Write-Host ("[START] launching {0} services in dependency order (wait {1}s per wave) ..." -f $AllModules.Count, $StartupWait) -ForegroundColor Green
  foreach ($w in $Waves) { Start-Wave $w }
  Write-Host "[OK] all start commands sent." -ForegroundColor Green
  Write-Host "     status: scripts\start-all.ps1 status"
  Write-Host "     logs:   scripts\start-all.ps1 logs <module>   (e.g. qiyu-live-api)"
  Write-Host "     web:    scripts\start-all.ps1 web   -> http://localhost:3000"
  Write-Host "     stop:   scripts\start-all.ps1 stop"
}
function Stop-All {
  Write-Host "[STOP] stopping all services ..." -ForegroundColor Yellow
  # jps-based kill: catches services even when pid files are missing/stale
  $jps = Join-Path $Jdk17 "bin\jps.exe"
  $pids = @()
  if (Test-Path $jps) {
    $pids = (& $jps -l 2>$null | Select-String -Pattern "qiyu-live" | ForEach-Object { ($_ -split '\s+')[0] })
  }
  foreach ($pidVal in $pids) {
    try {
      Stop-Process -Id ([int]$pidVal) -Force -ErrorAction Stop
      Write-Host ("  [OK] java pid {0} stopped" -f $pidVal)
    } catch {
      Write-Host ("  [-] pid {0}: not running" -f $pidVal)
    }
  }
  # also clean pid-file tracked services (e.g. web dev server)
  foreach ($mod in ($AllModules + @("web"))) {
    $f = Join-Path $LogDir "$mod.pid"
    if (Test-Path $f) {
      $filePid = [int](Get-Content $f -Raw)
      if ($pids -notcontains "$filePid") {
        try { Stop-Process -Id $filePid -Force -ErrorAction Stop; Write-Host ("  [OK] {0} (pid {1}) stopped" -f $mod, $filePid) } catch {}
      }
      Remove-Item $f -Force -ErrorAction SilentlyContinue
    }
  }
  Write-Host "[OK] all stopped" -ForegroundColor Green
}
function Show-Status {
  Write-Host "Middleware:"
  foreach ($name in $InfraPorts.Keys) {
    if (Test-Port $InfraPorts[$name]) {
      Write-Host ("  [*] {0,-10} port {1} up" -f $name, $InfraPorts[$name]) -ForegroundColor Green
    } else {
      Write-Host ("  [ ] {0,-10} port {1} down (scripts\start-all.ps1 infra)" -f $name, $InfraPorts[$name]) -ForegroundColor Yellow
    }
  }
  Write-Host "Services:"
  foreach ($mod in $AllModules) {
    if (Test-Running $mod) {
      $pidVal = Get-Content (Join-Path $LogDir "$mod.pid")
      Write-Host ("  [*] {0,-32} running (pid {1})" -f $mod, $pidVal)
    } else {
      Write-Host ("  [ ] {0,-32} not running" -f $mod)
    }
  }
  Write-Host "Web (vite):"
  if (Test-Running "web") { Write-Host ("  [*] web_live dev server running (pid {0}) -> http://localhost:3000" -f (Get-Content (Join-Path $LogDir "web.pid"))) }
  else { Write-Host "  [ ] not running (scripts\start-all.ps1 web)" }
}
function Show-Logs($mod) {
  if (-not $mod) { Write-Host "Usage: scripts\start-all.ps1 logs <module>"; exit 1 }
  $f = Join-Path $LogDir "$mod.log"
  if (-not (Test-Path $f)) { Write-Host "No log: $f"; exit 1 }
  Write-Host "=== $mod log (Ctrl+C to exit) ==="
  Get-Content $f -Tail 100 -Wait
}
function Start-Web {
  if (Test-Running "web") { Write-Host "[=] web dev server already running"; return }
  $webDir = Join-Path $ProjectRoot "web_live"
  if (-not (Test-Path (Join-Path $webDir "node_modules"))) {
    Write-Host "[WEB] node_modules missing, running pnpm install ..." -ForegroundColor Cyan
    Push-Location $webDir; pnpm install; Pop-Location
  }
  $out = Join-Path $LogDir "web.log"
  $err = Join-Path $LogDir "web.err.log"
  Write-Host "[WEB] starting vite dev server -> http://localhost:3000" -ForegroundColor Cyan
  $p = Start-Process -FilePath "cmd.exe" -ArgumentList "/c npx vite --port 3000" -WorkingDirectory $webDir -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
  Set-Content -Path (Join-Path $LogDir "web.pid") -Value $p.Id
}

# ---------- entry ----------
switch ($Action) {
  "infra"   { Start-Infra }
  "build"   { Build-All }
  "start"   { Start-All }
  "stop"    { Stop-All }
  "restart" { Stop-All; Start-Sleep -Seconds 2; Start-All }
  "status"  { Show-Status }
  "logs"    { Show-Logs $LogModule }
  "web"     { Start-Web }
  default   { Write-Host "Usage: scripts\start-all.ps1 [infra|build|start|stop|restart|status|logs <module>|web]"; exit 1 }
}
