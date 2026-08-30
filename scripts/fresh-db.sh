#!/usr/bin/env bash
# KEYSTONE — clean database + migration confirmation.  Usage: ./scripts/fresh-db.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo ""
echo "KEYSTONE — clean database migration check"
echo ""

if ! docker info >/dev/null 2>&1; then
  echo "X Docker is not running. Start Docker and retry." >&2
  exit 1
fi

echo "1/3  Wiping any existing database volume..."
docker compose down -v >/dev/null 2>&1 || true

echo "2/3  Building backend and starting db + backend..."
docker compose up -d --build db backend

echo "3/3  Waiting for migrations + startup..."
healthy=false
for _ in $(seq 1 60); do
  if curl -fs http://localhost:8080/api/health >/dev/null 2>&1; then
    healthy=true; break
  fi
  sleep 2
done

echo ""
echo "---- Flyway log lines ----"
docker compose logs backend 2>/dev/null | grep -iE "flyway|migrating|successfully applied|schema" || true

echo ""
if $healthy; then
  echo "PASS — clean DB migrated and the API is healthy."
  echo "Verify the seed data with:  ./scripts/verify.sh"
else
  echo "The API is not healthy yet. Inspect the full log:"
  echo "   docker compose logs -f backend"
fi
