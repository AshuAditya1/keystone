# =====================================================================
#  KEYSTONE — API smoke test.
#
#  Exercises the running API the way a reviewer would:
#    * health is public
#    * login returns a JWT for each seed role
#    * a protected endpoint rejects anonymous calls (401)
#    * the manager-only endpoint accepts MANAGER and rejects others (403)
#    * bad credentials are rejected (401)
#
#  Usage:   .\scripts\verify.ps1        (stack must already be running)
# =====================================================================

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api"
$pass = 0; $fail = 0

function Check($name, [scriptblock]$test) {
    try {
        if (& $test) { Write-Host "  PASS  $name" -ForegroundColor Green; $script:pass++ }
        else         { Write-Host "  FAIL  $name" -ForegroundColor Red;   $script:fail++ }
    } catch {
        Write-Host "  FAIL  $name  ($($_.Exception.Message))" -ForegroundColor Red
        $script:fail++
    }
}

function Login($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri "$base/auth/login" -Method Post -Body $body -ContentType "application/json"
    return $res.token
}

Write-Host ""
Write-Host "KEYSTONE — API smoke test against $base" -ForegroundColor Cyan
Write-Host ""

# 1. Health is public
Check "health endpoint is public and UP" {
    (Invoke-RestMethod -Uri "$base/health").status -eq "UP"
}

# 2. Each seed role can log in
$emails = @("manager@meridian.dev","dispatcher@meridian.dev","tech1@meridian.dev","alice@acme.dev")
$tokens = @{}
foreach ($e in $emails) {
    Check "login: $e" {
        $t = Login $e "ChangeMe123!"
        if ($t) { $script:tokens[$e] = $t; $true } else { $false }
    }
}

# 3. Protected endpoint rejects anonymous
Check "protected /ping/authenticated rejects anonymous (401)" {
    try { Invoke-RestMethod -Uri "$base/ping/authenticated" | Out-Null; $false }
    catch { $_.Exception.Response.StatusCode.value__ -eq 401 }
}

# 4. Protected endpoint accepts a valid token
Check "protected /ping/authenticated accepts a token" {
    $h = @{ Authorization = "Bearer $($tokens['tech1@meridian.dev'])" }
    (Invoke-RestMethod -Uri "$base/ping/authenticated" -Headers $h).role -eq "TECHNICIAN"
}

# 5. Manager-only endpoint accepts MANAGER
Check "manager-only endpoint accepts MANAGER" {
    $h = @{ Authorization = "Bearer $($tokens['manager@meridian.dev'])" }
    (Invoke-RestMethod -Uri "$base/ping/manager" -Headers $h).message -ne $null
}

# 6. Manager-only endpoint rejects a TECHNICIAN (403)
Check "manager-only endpoint rejects TECHNICIAN (403)" {
    $h = @{ Authorization = "Bearer $($tokens['tech1@meridian.dev'])" }
    try { Invoke-RestMethod -Uri "$base/ping/manager" -Headers $h | Out-Null; $false }
    catch { $_.Exception.Response.StatusCode.value__ -eq 403 }
}

# 7. Bad credentials rejected
Check "bad credentials are rejected (401)" {
    try { Login "manager@meridian.dev" "wrong-password" | Out-Null; $false }
    catch { $_.Exception.Response.StatusCode.value__ -eq 401 }
}

Write-Host ""
if ($fail -eq 0) {
    Write-Host "All $pass checks passed. Auth + RBAC are working end-to-end." -ForegroundColor Green
} else {
    Write-Host "$pass passed, $fail failed." -ForegroundColor Yellow
    exit 1
}
