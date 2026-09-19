<#
Qiyu Live Platform — PowerShell startup script
Usage:
  .\scripts\start.ps1            # auto build + start all services
  .\scripts\start.ps1 build      # only build mvn clean install -DskipTests
  .\scripts\start.ps1 start      # only start
  .\scripts\start.ps1 stop       # stop all
  .\scripts\start.ps1 restart    # restart all
  .\scripts\start.ps1 status     # show status
  .\scripts\start.ps1 logs <module> # watch log e.g. logs qiyu-live-api
Env:
  $env:JAVA_OPTS
  $env:NACOS_ADDR
  $env:STARTUP_WAIT
#>
$SCRIPT_DIR = $PSScriptRoot
$PROJECT_ROOT = Split-Path (Split-Path $SCRIPT_DIR -Parent) -Parent
$LOG_DIR = Join-Path $PROJECT_ROOT "logs"
if (-not (Test-Path $LOG_DIR)) { New-Item -ItemType Directory -Path $LOG_DIR | Out-Null }
Set-Location $PROJECT_ROOT
if (-not $env:JAVA_OPTS) { $env:JAVA_OPTS = "-Xms128m -Xmx384m" }
if (-not $env:NACOS_ADDR) { $env:NACOS_ADDR = "127.0.0.1:8848" }
if (-not $env:STARTUP_WAIT) { $env:STARTUP_WAIT = 10 }
$WAVE0 = @("qiyu-live-bank-provider", "qiyu-live-account-provider", "qiyu-live-id-generate-provider", "qiyu-live-im-provider", "qiyu-live-stream-provider")
$WAVE1 = @("qiyu-live-user-provider", "qiyu-live-im-core-server", "qiyu-live-gateway", "qiyu-live-bank-api")
$WAVE2 = @("qiyu-live-im-router-provider")
$WAVE3 = @("qiyu-live-living-provider")
$WAVE4 = @("qiyu-live-msg-provider", "qiyu-live-gift-provider")
$WAVE5 = @("qiyu-live-api")
$ALL_MODULES = $WAVE0 + $WAVE1 + $WAVE2 + $WAVE3 + $WAVE4 + $WAVE5
function Find-Jar {
    param($mod)
    $jarPath = Get-ChildItem "$mod/target/*.jar" -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -notmatch '(sources\.jar|javadoc\.jar|\.jar\.original)$'
    } | Select-Object -First 1
    if ($jarPath) { return $jarPath.FullName }
    return $null
}
function Is-Running {
    param($mod)
    $pidFile = Join-Path $LOG_DIR "$mod.pid"
    if (-not (Test-Path $pidFile)) { return $false }
    $pid = Get-Content $pidFile -Raw
    try {
        Get-Process -Id $pid -ErrorAction Stop | Out-Null
        return $true
    } catch {
        return $false
    }
}
function Check-Nacos {
    try {
        Invoke-WebRequest -Uri "http://$($env:NACOS_ADDR)/nacos/" -TimeoutSec 3 -UseBasicParsing | Out-Null
    } catch {
        Write-Host "WARNING: Nacos($($env:NACOS_ADDR)) is not ready. Start Nacos/MySQL/Redis/RocketMQ first."
        $ans = Read-Host "Continue to start services? [y/N]"
        if ($ans -notmatch '^[Yy]$') { exit 1 }
    }
}
function Build-All {
    Write-Host "BUILD: mvn clean install -DskipTests -DskipDocker"
    # ===== 打包强制使用JDK17 =====
    $env:JAVA_HOME="D:\enviroment\javaenviroment\jdk17"
    $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
    mvn clean install -DskipTests -DskipDocker
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Build failed"
        exit 1
    }
    Write-Host "INFO: Build complete"
}


function Start-Module {
    param($mod)
    $jar = Find-Jar $mod
    if (-not $jar) {
        Write-Host "  SKIP $mod : jar not found in target, run build first"
        return 1
    }
    if (Is-Running $mod) {
        $pid = Get-Content (Join-Path $LOG_DIR "$mod.pid")
        Write-Host "  SKIP $mod : already running (pid $pid)"
        return 0
    }
    Write-Host "  START $mod"
    $logFile = Join-Path $LOG_DIR "$mod.log"
    $javaExe = "D:\enviroment\javaenviroment\jdk17\bin\java.exe"
    $argList = @("$($env:JAVA_OPTS)", "-jar", "`"$jar`"")
    # 用cmd启动，2>&1合并输出，规避Start-Process重定向冲突bug
    $proc = Start-Process cmd.exe -ArgumentList "/c $javaExe $($argList -join ' ') >> `"$logFile`" 2>&1" -PassThru -NoNewWindow
    $proc.Id | Out-File (Join-Path $LOG_DIR "$mod.pid")
}

function Start-Wave {
    param($desc, $modules)
    Write-Host "--- $desc ---"
    foreach($mod in $modules) {
        Start-Module $mod | Out-Null
    }
    Start-Sleep -Seconds $env:STARTUP_WAIT
}
function Start-All {
    Check-Nacos
    $needBuild = $false
    foreach($mod in $ALL_MODULES) {
        if (-not (Find-Jar $mod)) {
            $needBuild = $true
            break
        }
    }
    if ($needBuild) { Build-All }
    Write-Host "START: total $($ALL_MODULES.Count) services, interval $($env:STARTUP_WAIT)s"
    Start-Wave "Wave0 base layer(no dubbo dependency)" $WAVE0
    Start-Wave "Wave1 depend on base" $WAVE1
    Start-Wave "Wave2 im router" $WAVE2
    Start-Wave "Wave3 live room" $WAVE3
    Start-Wave "Wave4 message/gift" $WAVE4
    Start-Wave "Wave5 main api entry" $WAVE5
    Write-Host "INFO: All startup commands sent."
    Write-Host "  Status : .\scripts\start.ps1 status"
    Write-Host "  Logs   : .\scripts\start.ps1 logs <module>"
    Write-Host "  Stop   : .\scripts\start.ps1 stop"
}
function Stop-All {
    Write-Host "STOP: stopping all services"
    foreach($mod in $ALL_MODULES) {
        $pidFile = Join-Path $LOG_DIR "$mod.pid"
        if (Test-Path $pidFile) {
            $pid = Get-Content $pidFile -Raw
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Host "  STOPPED $mod (pid $pid)"
            } catch {
                Write-Host "  NOTE $mod : process already exited(pid $pid)"
            }
            Remove-Item $pidFile -ErrorAction SilentlyContinue
        }
    }
}
function Status-All {
    Write-Host "Service status:"
    foreach($mod in $ALL_MODULES) {
        if (Is-Running $mod) {
            $pid = Get-Content (Join-Path $LOG_DIR "$mod.pid")
            Write-Host ("  RUNNING {0,-32} pid={1}" -f $mod,$pid)
        } else {
            Write-Host ("  STOPPED {0,-32}" -f $mod)
        }
    }
}
function Tail-Log {
    param($mod)
    if (-not $mod) {
        Write-Host "Usage: .\scripts\start.ps1 logs <module>"
        exit 1
    }
    $logFile = Join-Path $LOG_DIR "$mod.log"
    if (-not (Test-Path $logFile)) {
        Write-Host "ERROR: log file not found $logFile"
        exit 1
    }
    Write-Host "=== $mod log (Ctrl+C exit) ==="
    Get-Content $logFile -Wait -Tail 100
}
$action = if ($args.Count -gt 0) { $args[0] } else { "start" }
switch ($action) {
    "build" { Build-All }
    "start" { Start-All }
    "stop" { Stop-All }
    "restart" { Stop-All; Start-Sleep -Seconds 2; Start-All }
    "status" { Status-All }
    "logs" { Tail-Log $args[1] }
    default {
        Write-Host "Usage: .\scripts\start.ps1 [build|start|stop|restart|status|logs <module>]"
        exit 1
    }
}
