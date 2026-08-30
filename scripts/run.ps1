# =====================================================================
#  KEYSTONE — run the whole stack with one command.
#
#  Usage (from anywhere):   .\scripts\run.ps1
#
#  Builds and starts Postgres + backend + frontend, waits for the API to
#  report healthy, then prints the URLs and seed logins.
# =====================================================================

$ErrorActionPreference = "Stop"

# Always operate from the repo root (the folder that holds docker-compose.yml).
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host ""
Write-Host "KEYSTONE — starting the full stack" -ForegroundColor Cyan
Write-Host "Repo: $repoRoot"
Write-Host ""

# --- 1. Is Docker installed & running? -------------------------------
try {
    docker info *> $null
} catch {
    Write-Host "X Docker does not appear to be running." -ForegroundColor Red
    Write-Host "  Start Docker Desktop, wait for 'Engine running', then re-run this script."
    exit 1
}

# --- 2. Build + start everything, detached ---------------------------
Write-Host "Building and starting containers (first run downloads images; be patient)..." -ForegroundColor Yellow
docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "X docker compose failed. Scroll up for the error." -ForegroundColor Red
    exit 1
}

# --- 3. Wait for the API health endpoint -----------------------------
Write-Host ""
Write-Host "Waiting for the backend to become healthy..." -ForegroundColor Yellow
$healthy = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 3
        if ($res.status -eq "UP") { $healthy = $true; break }
    } catch {
        Start-Sleep -Seconds 2
    }
}

Write-Host ""
if ($healthy) {
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host " KEYSTONE is up." -ForegroundColor Green
    Write-Host "========================================================" -ForegroundColor Green
    Write-Host " Frontend :  http://localhost:3000"
    Write-Host " API      :  http://localhost:8080/api/health"
    Write-Host " Swagger  :  http://localhost:8080/swagger-ui.html"
    Write-Host ""
    Write-Host " Seed logins (password: ChangeMe123!)"
    Write-Host "   manager@meridian.dev      (MANAGER)"
    Write-Host "   dispatcher@meridian.dev   (DISPATCHER)"
    Write-Host "   tech1@meridian.dev        (TECHNICIAN)"
    Write-Host "   alice@acme.dev            (CUSTOMER)"
    Write-Host ""
    Write-Host " Stop with:  .\scripts\stop.ps1"
    Write-Host "========================================================" -ForegroundColor Green
} else {
    Write-Host "! The API did not report healthy within ~2 minutes." -ForegroundColor Yellow
    Write-Host "  It may still be starting. Check logs with:"
    Write-Host "     docker compose logs -f backend"
}
