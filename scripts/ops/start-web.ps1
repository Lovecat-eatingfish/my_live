#requires -Version 5.1
param(
  [switch]$SkipInstall
)

$ErrorActionPreference = "Continue"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..\..").Path
$LogDir = Join-Path $ProjectRoot "logs"
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

function Test-Running($name) {
  $f = Join-Path $LogDir "$name.pid"
  if (Test-Path $f) {
    $raw = Get-Content $f -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) { return $false }
    $pidVal = 0
    if ([int]::TryParse($raw.Trim(), [ref]$pidVal)) {
      try {
        $proc = Get-Process -Id $pidVal -ErrorAction Stop
        if ($proc.ProcessName -in @("node", "node.exe")) { return $true }
      } catch {}
    }
  }
  return $false
}

function Start-Frontend($name, $dir, $port) {
  if (Test-Running $name) {
    $pidVal = Get-Content (Join-Path $LogDir "$name.pid")
    Write-Host "[=] $name already running (pid $pidVal) -> http://localhost:$port"
    return
  }
  if (-not (Test-Path (Join-Path $dir "node_modules"))) {
    if (-not $SkipInstall) {
      Write-Host "[WEB] node_modules missing, running pnpm install for $name ..."
      Push-Location $dir; pnpm install; Pop-Location
    } else {
      Write-Host "[X] $name node_modules missing"
      return
    }
  }
  $out = Join-Path $LogDir "$name.log"
  $err = Join-Path $LogDir "$name.err.log"
  Write-Host "[>] starting $name -> http://localhost:$port"
  $p = Start-Process -FilePath "cmd.exe" -ArgumentList "/c npx vite --port $port" -WorkingDirectory $dir -RedirectStandardOutput $out -RedirectStandardError $err -PassThru -WindowStyle Hidden
  Set-Content -Path (Join-Path $LogDir "$name.pid") -Value $p.Id
}

Write-Host "============================================================"
Write-Host "   Qiyu Live Frontend Start"
Write-Host "============================================================"

$webLive = Join-Path $ProjectRoot "web_live"
$webAdmin = Join-Path $ProjectRoot "web_admin"

Start-Frontend "web_live"   $webLive   3000
Start-Frontend "web_admin"  $webAdmin  3005

Write-Host ""
Write-Host "[OK] Frontend started"
Write-Host "  web_live  -> http://localhost:3000"
Write-Host "  web_admin -> http://localhost:3005"
