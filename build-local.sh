#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[ZipAI] .env file not found: $ENV_FILE"
  echo "[ZipAI] Copy .env.example to .env and fill in the required values."
  exit 1
fi

set -a
# Remove Windows CR characters before loading the environment file.
# shellcheck disable=SC1090
source <(sed 's/\r$//' "$ENV_FILE")
set +a

missing=()
for name in DB_URL DB_USERNAME DB_PASSWORD; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  echo "[ZipAI] Required environment variables are missing: ${missing[*]}"
  exit 1
fi

echo "[ZipAI] Environment loaded from .env"
echo "[ZipAI] Running clean build..."
cd "$SCRIPT_DIR"
exec ./gradlew clean build "$@"
