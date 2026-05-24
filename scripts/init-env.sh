#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
EXAMPLE_FILE="$ROOT_DIR/.env.example"

if [[ -f "$ENV_FILE" ]]; then
  echo ".env already exists at: $ENV_FILE"
  echo "Edit it directly if you need changes."
  exit 0
fi

cp "$EXAMPLE_FILE" "$ENV_FILE"
echo "Created $ENV_FILE from .env.example"
echo "Next: edit .env once with your real values (DB, JWT, GEMINI_API_KEY)."
