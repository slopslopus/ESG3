#!/bin/sh
# Start postgres + backend via docker compose. Run from backend/ directory.
set -e
cd "$(dirname "$0")"
docker compose --env-file ../.env -f docker-compose.yml up -d --wait
