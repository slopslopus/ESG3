$ErrorActionPreference = 'Stop'
# Start postgres + backend via docker compose. Run from backend\ directory.
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)
docker compose --env-file ../.env -f docker-compose.yml up -d --wait
