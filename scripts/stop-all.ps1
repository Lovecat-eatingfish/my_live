#requires -Version 5.1
# Thin shim: real implementation moved to scripts\ops\stop-all.ps1 (2026-09-19 re-org).
# This file keeps existing docs/scripts working: powershell -File scripts\stop-all.ps1 <args>
& "$PSScriptRoot\ops\stop-all.ps1" @args
exit $LASTEXITCODE
