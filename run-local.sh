#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ZipAI] .env file not found: $ENV_FILE"
  echo "[ZipAI] Copy .env.example to .env and set VWORLD_API_KEY."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -z "${VWORLD_API_KEY:-}" ]]; then
  echo "[ZipAI] VWORLD_API_KEY is empty in .env"
  exit 1
fi

echo "[ZipAI] Environment loaded from .env"
echo "[ZipAI] Starting Spring Boot..."
cd "$SCRIPT_DIR"
exec ./gradlew bootRun
