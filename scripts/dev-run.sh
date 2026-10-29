#!/usr/bin/env bash
# Start the API for local development.
#   ./scripts/dev-run.sh
# Loads .env (Spring does not read it natively), then runs the app with
# spring-boot-devtools active so a recompile hot-restarts the context.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

exec ./mvnw spring-boot:run
