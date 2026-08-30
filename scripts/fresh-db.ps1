# =====================================================================
#  KEYSTONE — fresh database + migration confirmation.
#
#  This is the "confirm the migrations run on a clean DB" check.
#  It wipes any existing data, then starts ONLY Postgres + backend and
#  watches Flyway apply V1 and V2 on a pristine database.
#
#  Usage:   .\scripts\fresh-db.ps1
# =====================================================================

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "KEYSTONE — clean database migration check" -ForegroundColor Cyan
Write-Host ""

try { docker info *> $null } catch {
    Write-Host "X Docker is not running. Start Docker Desktop and retry." -ForegroundColor Red
    exit 1
}

Write-Host "1/3  Wiping any existing database volume..." -ForegroundColor Yellow
docker compose down -v *> $null

Write-Host "2/3  Building backend and starting db + backend..." -ForegroundColor Yellow
docker compose up -d --build db backend
if ($LASTEXITCODE -ne 0) { Write-Host "X compose failed." -ForegroundColor Red; exit 1 }

Write-Host "3/3  Waiting for migrations + startup..." -ForegroundColor Yellow
$healthy = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 3
        if ($res.status -eq "UP") { $healthy = $true; break }
    } catch { Start-Sleep -Seconds 2 }
}

Write-Host ""
Write-Host "---- Flyway log lines ----" -ForegroundColor Cyan
docker compose logs backend 2>$null | Select-String -Pattern "flyway|Migrating|Successfully applied|Schema" -CaseSensitive:$false

Write-Host ""
if ($healthy) {
    Write-Host "PASS — clean DB migrated and the API is healthy." -ForegroundColor Green
    Write-Host "Verify the seed data with:  .\scripts\verify.ps1"
} else {
    Write-Host "The API is not healthy yet. Inspect the full log:" -ForegroundColor Yellow
    Write-Host "   docker compose logs -f backend"
}
