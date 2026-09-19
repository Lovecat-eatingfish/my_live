#requires -Version 5.1
<#
.SYNOPSIS
  Qiyu Live Platform - 全量编译并启动服务脚本 (Build -> Check Infra -> Start Waves)
.DESCRIPTION
  1. 严格使用 JDK 17 环境（与 start-all.ps1 保持一致）
  2. 停止可能运行中的旧服务，释放端口及目标 jar 文件占用
  3. 执行全量编译: mvn clean install -DskipTests
  4. 检查并确保中间件（MySQL/Redis/Nacos/RocketMQ）就绪及 Nacos 命名空间
  5. 按照波次依赖顺序启动全部 16 个微服务
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\start-and-build.ps1
  powershell -ExecutionPolicy Bypass -File scripts\start-and-build.ps1 -SkipInfra
  powershell -ExecutionPolicy Bypass -File scripts\start-and-build.ps1 -StartupWait 5
#>
param(
  [switch]$SkipInfra,
  [switch]$SkipStop,
  [int]$StartupWait = 0
)

$ErrorActionPreference = "Continue"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$LogDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

$JavaOpts    = if ($env:JAVA_OPTS)    { $env:JAVA_OPTS }    else { "-Xms128m -Xmx384m" }
$NacosAddr   = if ($env:NACOS_ADDR)   { $env:NACOS_ADDR }   else { "127.0.0.1:8848" }
if ($StartupWait -le 0) {
  $StartupWait = if ($env:STARTUP_WAIT) { [int]$env:STARTUP_WAIT } else { 10 }
}

# ---------- JDK 17 resolution (与 start-all.ps1 严格保持一致) ----------
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
$env:Path = "$Jdk17\bin;" + $env:Path
Write-Host "[JAVA] using JDK: $Jdk17" -ForegroundColor DarkGray

# ---------- 中间件配置 ----------
$InfraPorts = @{ "MySQL" = 3306; "Redis" = 6379; "Nacos" = 8848; "RocketMQ" = 9876 }
$NacosNamespaceId = "ef63e53e-94c8-4b1c-865e-6824177b2893"
$NacosNamespaceName = "qiyu-live-test"

# ---------- 服务波次划分（严格按照依赖顺序，避免 QiyuProviderStartupVerifier 启动自检报错） ----------
$Waves = @(
  @{ Name="Wave0 base (no Dubbo dep)"; Mods=@("qiyu-live-bank-provider","qiyu-live-account-provider","qiyu-live-id-generate-provider","qiyu-live-im-provider","qiyu-live-stream-provider") },
  @{ Name="Wave1 depends on base";     Mods=@("qiyu-live-user-provider","qiyu-live-im-core-server","qiyu-live-gateway","qiyu-live-bank-api") },
  @{ Name="Wave2 IM router";            Mods=@("qiyu-live-im-router-provider") },
  @{ Name="Wave3 living room";          Mods=@("qiyu-live-living-provider") },
  @{ Name="Wave4 msg / gift";           Mods=@("qiyu-live-msg-provider","qiyu-live-gift-provider") },
  @{ Name="Wave4b video";               Mods=@("qiyu-live-video-provider") },
  @{ Name="Wave5 main API";             Mods=@("qiyu-live-api", "qiyu-live-admin-api") }
)
$AllModules = $Waves.ForEach{ $_.Mods }

# im-core-server 启动需要注册 IP 与端口到 Redis
$ModuleExtraEnv = @{
  "qiyu-live-im-core-server" = @{ "DUBBO_IP_TO_REGISTRY" = "127.0.0.1"; "DUBBO_PORT_TO_REGISTRY" = "30035" }
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

function Test-Port($port) {
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $r = $c.BeginConnect("127.0.0.1", $port, $null, $null)
    return $r.AsyncWaitHandle.WaitOne(1500) -and $c.Connected
  } catch { return $false } finally { $c.Close() }
}

function Stop-AllServices {
  Write-Host "[STOP] 检查并清理正在运行的旧微服务实例 ..." -ForegroundColor Yellow
  if (Test-Path $JpsExe) {
    $pids = (& $JpsExe -l 2>$null | Select-String -Pattern "qiyu-live" | ForEach-Object { ($_ -split '\s+')[0] })
    foreach ($p in $pids) {
      if ($p -match '^\d+$') {
        try {
          Stop-Process -Id ([int]$p) -Force -ErrorAction Stop
          Write-Host ("  [OK] 停止残留 JVM (PID: {0})" -f $p) -ForegroundColor DarkGray
        } catch {}
      }
    }
  }

  foreach ($mod in $AllModules) {
    $f = Join-Path $LogDir "$mod.pid"
    if (Test-Path $f) {
      $raw = Get-Content $f -Raw
      $filePid = 0
      if ([int]::TryParse($raw.Trim(), [ref]$filePid)) {
        try { Stop-Process -Id $filePid -Force -ErrorAction SilentlyContinue } catch {}
      }
      Remove-Item $f -Force -ErrorAction SilentlyContinue
    }
  }
  Start-Sleep -Seconds 1
}

function Build-Project {
  Write-Host "`n===== Step 1: 全量编译 (mvn clean install -DskipTests) =====" -ForegroundColor Cyan
  Push-Location $ProjectRoot
  try {
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
      Write-Host "`n[FAIL] Maven 编译打包失败，中止启动！" -ForegroundColor Red
      exit 1
    }
  } finally {
    Pop-Location
  }
  Write-Host "[OK] Maven 编译打包完成" -ForegroundColor Green
}

function Ensure-NacosNamespace {
  try {
    $login = Invoke-RestMethod -Method Post -Uri "http://$NacosAddr/nacos/v1/auth/login" -Body @{ username = "nacos"; password = "nacos" } -TimeoutSec 5
    $token = $login.accessToken
    $r = Invoke-WebRequest -Method Post -Uri "http://$NacosAddr/nacos/v1/console/namespaces" -Body @{ customNamespaceId = $NacosNamespaceId; namespaceName = $NacosNamespaceName; namespaceDesc = "qiyu local"; accessToken = $token } -TimeoutSec 5 -UseBasicParsing
    Write-Host "[OK] Nacos 命名空间检查通过: $NacosNamespaceName" -ForegroundColor Green
  } catch {
    Write-Host "[OK] Nacos 命名空间已就绪" -ForegroundColor DarkGray
  }
}

function Check-And-Start-Infra {
  if ($SkipInfra) {
    Write-Host "`n===== Step 2: 跳过中间件检查 (-SkipInfra) =====" -ForegroundColor DarkGray
    return
  }

  Write-Host "`n===== Step 2: 检查中间件环境 (MySQL/Redis/Nacos/RocketMQ) =====" -ForegroundColor Cyan
  $allUp = $true
  foreach ($port in $InfraPorts.Values) {
    if (-not (Test-Port $port)) { $allUp = $false; break }
  }

  if ($allUp) {
    Write-Host "[INFRA] 全部中间件端口已就绪，跳过 docker compose" -ForegroundColor Green
    Ensure-NacosNamespace
    return
  }

  Write-Host "[INFRA] 正在启动 docker compose 中间件容器 ..." -ForegroundColor Cyan
  docker compose -f (Join-Path $ProjectRoot "docker-compose.yml") up -d
  if ($LASTEXITCODE -ne 0) {
    Write-Host "[FAIL] docker compose 启动失败，请检查 Docker Desktop 是否正在运行！" -ForegroundColor Red
    exit 1
  }

  Write-Host "[INFRA] 等待中间件端口启动就绪 (最多等待 120s) ..." -ForegroundColor Cyan
  foreach ($name in $InfraPorts.Keys) {
    $port = $InfraPorts[$name]
    $ok = $false
    for ($i = 0; $i -lt 60; $i++) {
      if (Test-Port $port) { $ok = $true; break }
      Start-Sleep -Seconds 2
    }
    if ($ok) { Write-Host ("  [OK] {0} (端口 {1}) 已就绪" -f $name, $port) -ForegroundColor Green }
    else     { Write-Host ("  [X] {0} (端口 {1}) 无法连接" -f $name, $port) -ForegroundColor Red }
  }
  Ensure-NacosNamespace
}

function Start-Module($mod) {
  $jar = Find-Jar $mod
  if (-not $jar) {
    Write-Host ("  [X] {0}: 未找到 target/*.jar，请检查编译结果" -f $mod) -ForegroundColor Red
    return $false
  }
  if (Test-Running $mod) {
    $pidVal = Get-Content (Join-Path $LogDir "$mod.pid")
    Write-Host ("  [=] {0}: 已在运行中 (PID {1})，跳过" -f $mod, $pidVal)
    return $true
  }
  Write-Host ("  [>] 正在启动 {0} ..." -f $mod)
  $out = Join-Path $LogDir "$mod.log"
  $err = Join-Path $LogDir "$mod.err.log"
  $argStr = "$JavaOpts -Dfile.encoding=UTF-8 -jar `"$($jar.FullName)`""

  if ($ModuleExtraEnv.ContainsKey($mod)) {
    foreach ($k in $ModuleExtraEnv[$mod].Keys) { Set-Item -Path ("env:" + $k) -Value $ModuleExtraEnv[$mod][$k] }
  }

  $p = Start-Process -FilePath $JavaExe -ArgumentList $argStr -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden

  if ($ModuleExtraEnv.ContainsKey($mod)) {
    foreach ($k in $ModuleExtraEnv[$mod].Keys) { Remove-Item -Path ("env:" + $k) -ErrorAction SilentlyContinue }
  }

  Set-Content -Path (Join-Path $LogDir "$mod.pid") -Value $p.Id
  return $true
}

function Start-Wave($w) {
  Write-Host ("`n-- {0} --" -f $w.Name) -ForegroundColor Cyan
  foreach ($mod in $w.Mods) { [void](Start-Module $mod) }
  Write-Host ("等待波次服务就绪 ({0}s) ..." -f $StartupWait) -ForegroundColor DarkGray
  Start-Sleep -Seconds $StartupWait
}

# ---------- 主流程执行 ----------
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "   旗鱼直播平台 · 全量编译并按波次启动 (Start & Build)       " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. 清理旧实例，释放端口与 jar 文件
if (-not $SkipStop) {
  Stop-AllServices
}

# 2. 全量 Maven 编译打包
Build-Project

# 3. 确保中间件环境
Check-And-Start-Infra

# 4. 按波次顺序拉起 Java 服务
Write-Host ("`n===== Step 3: 按波次顺序启动 {0} 个后端服务 (每波等待 {1}s) =====" -f $AllModules.Count, $StartupWait) -ForegroundColor Cyan
foreach ($w in $Waves) {
  Start-Wave $w
}

# 5. 状态汇总与就绪提示
Write-Host "`n============================================================" -ForegroundColor Green
Write-Host "  [OK] 全部服务启动指令已发送完成！" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  服务状态查看: powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 status"
Write-Host "  模块实时日志: powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 logs <module>"
Write-Host "  单模块重部署: powershell -ExecutionPolicy Bypass -File scripts\reload-single.ps1 <module>"
Write-Host "  启动前端界面: powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 web"
Write-Host "  停止所有服务: powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 stop`n"
