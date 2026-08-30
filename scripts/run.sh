#!/usr/bin/env bash
# KEYSTONE — run the whole stack with one command.  Usage: ./scripts/run.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo ""
echo "KEYSTONE — starting the full stack"
echo "Repo: $(pwd)"
echo ""

if ! docker info >/dev/null 2>&1; then
  echo "X Docker does not appear to be running. Start Docker and retry." >&2
  exit 1
fi

echo "Building and starting containers (first run downloads images)..."
docker compose up -d --build

echo ""
echo "Waiting for the backend to become healthy..."
healthy=false
for _ in $(seq 1 60); do
  if curl -fs http://localhost:8080/api/health >/dev/null 2>&1; then
    healthy=true; break
  fi
  sleep 2
done

echo ""
if $healthy; then
  cat <<'EOF'
========================================================
 KEYSTONE is up.
========================================================
 Frontend :  http://localhost:3000
 API      :  http://localhost:8080/api/health
 Swagger  :  http://localhost:8080/swagger-ui.html

 Seed logins (password: ChangeMe123!)
   manager@meridian.dev      (MANAGER)
   dispatcher@meridian.dev   (DISPATCHER)
   tech1@meridian.dev        (TECHNICIAN)
   alice@acme.dev            (CUSTOMER)

 Stop with:  ./scripts/stop.sh
========================================================
EOF
else
  echo "! The API did not report healthy within ~2 minutes."
  echo "  Check logs:  docker compose logs -f backend"
fi
