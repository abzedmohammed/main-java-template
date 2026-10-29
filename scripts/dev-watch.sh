#!/usr/bin/env bash
# Watch src/main for changes and recompile; spring-boot-devtools then hot-restarts the running app.
#   ./scripts/dev-watch.sh        (run in a second terminal next to ./scripts/dev-run.sh)
cd "$(dirname "$0")/.."
mkdir -p target && touch target/.watch-stamp
echo "Watching src/main for changes (Ctrl+C to stop)..."
while true; do
  if [ -n "$(find src/main -type f -newer target/.watch-stamp 2>/dev/null | head -1)" ]; then
    touch target/.watch-stamp
    echo "[$(date +%T)] change detected, recompiling..."
    bash scripts/dev-reload.sh
  fi
  sleep 2
done
