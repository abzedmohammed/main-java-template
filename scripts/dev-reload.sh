#!/usr/bin/env bash
# Recompile main sources so the running spring-boot-devtools instance
# hot-restarts the application context. Cheap, offline, incremental.
#   ./scripts/dev-reload.sh
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -o -q compile
