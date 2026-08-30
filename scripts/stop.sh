#!/usr/bin/env bash
# KEYSTONE — stop the stack.  Usage: ./scripts/stop.sh  [--wipe]
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ "${1:-}" == "--wipe" ]]; then
  echo "Stopping and WIPING the database volume..."
  docker compose down -v
  echo "Done. Next ./scripts/run.sh starts from a clean database."
else
  echo "Stopping containers (database volume kept)..."
  docker compose down
  echo "Done. Data preserved. Start again with ./scripts/run.sh."
fi
