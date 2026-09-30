#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GITIGNORE="$SCRIPT_DIR/.gitignore"

if [[ ! -f "$GITIGNORE" ]]; then
  printf '.env\n' > "$GITIGNORE"
  echo "[ZipAI] Created .gitignore with .env"
  exit 0
fi

if grep -qxF '.env' "$GITIGNORE"; then
  echo "[ZipAI] .env is already in .gitignore"
else
  printf '\n.env\n' >> "$GITIGNORE"
  echo "[ZipAI] Added .env to .gitignore"
fi
