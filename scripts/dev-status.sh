#!/usr/bin/env bash
# Report whether the local dev server is up, and which PID owns the port.
#
# `spring-boot:run` forks a separate JVM, so the app outlives the shell that
# launched it. Ask the port who owns it rather than tracking the wrapper.
#   ./scripts/dev-status.sh
APP_PORT="${APP_PORT:-8080}"

pid=$(netstat -ano 2>/dev/null |
  tr -d '\r' |
  awk -v p=":${APP_PORT}" '$1=="TCP" && $2 ~ p"$" && $4=="LISTENING" {print $5; exit}')

if [ -z "$pid" ]; then
  echo "DOWN — nothing listening on ${APP_PORT}. Start it with ./scripts/dev-run.sh"
  exit 1
fi

echo "UP — port ${APP_PORT} owned by PID ${pid}"
exit 0
