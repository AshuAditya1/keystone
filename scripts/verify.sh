#!/usr/bin/env bash
# KEYSTONE — API smoke test (stack must be running).  Usage: ./scripts/verify.sh
set -uo pipefail
BASE="http://localhost:8080/api"
pass=0; fail=0

ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL  $1"; fail=$((fail+1)); }

login() {  # login email password -> prints token (empty on failure)
  curl -fs -X POST "$BASE/auth/login" -H "Content-Type: application/json" \
    -d "{\"email\":\"$1\",\"password\":\"$2\"}" 2>/dev/null \
    | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}

code() {  # code URL [token] -> prints HTTP status code
  if [[ -n "${2:-}" ]]; then
    curl -s -o /dev/null -w "%{http_code}" "$1" -H "Authorization: Bearer $2"
  else
    curl -s -o /dev/null -w "%{http_code}" "$1"
  fi
}

echo ""
echo "KEYSTONE — API smoke test against $BASE"
echo ""

# 1. Health public
if curl -fs "$BASE/health" 2>/dev/null | grep -q '"status":"UP"'; then
  ok "health endpoint is public and UP"; else bad "health endpoint is public and UP"; fi

# 2. Logins
MGR=$(login manager@meridian.dev 'ChangeMe123!')
DISP=$(login dispatcher@meridian.dev 'ChangeMe123!')
TECH=$(login tech1@meridian.dev 'ChangeMe123!')
CUST=$(login alice@acme.dev 'ChangeMe123!')
[[ -n "$MGR"  ]] && ok "login: manager"    || bad "login: manager"
[[ -n "$DISP" ]] && ok "login: dispatcher" || bad "login: dispatcher"
[[ -n "$TECH" ]] && ok "login: technician" || bad "login: technician"
[[ -n "$CUST" ]] && ok "login: customer"   || bad "login: customer"

# 3. Protected endpoint rejects anonymous / accepts token
[[ "$(code "$BASE/ping/authenticated")"       == 401 ]] && ok "protected rejects anonymous (401)"     || bad "protected rejects anonymous (401)"
[[ "$(code "$BASE/ping/authenticated" "$TECH")" == 200 ]] && ok "protected accepts a token (200)"      || bad "protected accepts a token (200)"

# 4. RBAC on the manager-only endpoint
[[ "$(code "$BASE/ping/manager" "$MGR")"  == 200 ]] && ok "manager-only accepts MANAGER (200)"       || bad "manager-only accepts MANAGER (200)"
[[ "$(code "$BASE/ping/manager" "$TECH")" == 403 ]] && ok "manager-only rejects TECHNICIAN (403)"    || bad "manager-only rejects TECHNICIAN (403)"

# 5. Bad credentials
BADCODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"manager@meridian.dev","password":"nope"}')
[[ "$BADCODE" == 401 ]] && ok "bad credentials rejected (401)" || bad "bad credentials rejected (401)"

echo ""
if [[ $fail -eq 0 ]]; then
  echo "All $pass checks passed. Auth + RBAC are working end-to-end."
else
  echo "$pass passed, $fail failed. Is the stack running (./scripts/run.sh)?"
  exit 1
fi
