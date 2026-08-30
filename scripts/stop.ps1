# =====================================================================
#  KEYSTONE — stop the stack.
#
#  Usage:                .\scripts\stop.ps1          (stop, keep data)
#                        .\scripts\stop.ps1 -Wipe    (stop AND wipe the DB)
# =====================================================================

param(
    [switch]$Wipe
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ($Wipe) {
    Write-Host "Stopping and WIPING the database volume..." -ForegroundColor Yellow
    docker compose down -v
    Write-Host "Done. Next '.\scripts\run.ps1' starts from a clean database." -ForegroundColor Green
} else {
    Write-Host "Stopping containers (database volume kept)..." -ForegroundColor Yellow
    docker compose down
    Write-Host "Done. Data preserved. Start again with '.\scripts\run.ps1'." -ForegroundColor Green
}
