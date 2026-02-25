#!/usr/bin/env bash
set -euo pipefail

DB_NAME=${DB_NAME:-main_java_template}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

echo "Creating database ${DB_NAME} if missing..."
psql -U "$DB_USER" -h localhost -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 || \
  psql -U "$DB_USER" -h localhost -c "CREATE DATABASE ${DB_NAME};"

echo "Done. Ensure your .env matches credentials."