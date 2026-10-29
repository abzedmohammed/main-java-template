#!/usr/bin/env bash
# Claude Code PostToolUse hook (Edit|Write|MultiEdit).
#
# When a src/main file changes and a dev server is listening on APP_PORT,
# recompile so the running spring-boot-devtools instance hot-restarts.
# Exits 2 with the compiler output when the build breaks, so the agent is
# woken with the error instead of leaving a stale server running.
#
# Reads the hook payload as JSON on stdin. No jq dependency (not installed
# on this machine) — file_path is pulled out with sed.
cd "$(dirname "$0")/.." || exit 0

APP_PORT="${APP_PORT:-8080}"

payload=$(cat)
file=$(printf '%s' "$payload" |
  sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)

# Only Java/resource sources feed the running app.
case "$file" in
  *src/main/*|*src\\\\main\\\\*) ;;
  *) exit 0 ;;
esac

# Nothing to reload if no server is up.
(exec 3<>"/dev/tcp/127.0.0.1/${APP_PORT}") 2>/dev/null || exit 0

if ! out=$(./mvnw -o -q compile 2>&1); then
  echo "Hot-reload compile failed after editing ${file}:"
  printf '%s\n' "$out" | grep -E '^\[ERROR\]' | head -20
  exit 2
fi

exit 0
