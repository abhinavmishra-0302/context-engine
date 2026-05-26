#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-}"
PASSWORD="${PASSWORD:-Password123}"
PRINT_TOKEN="${PRINT_TOKEN:-1}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/test-auth.sh --email you@example.com [--password Password123]

Behavior:
  1) tries /auth/login
  2) if login fails, tries /auth/signup
  3) prints TOKEN when successful

Env:
  BASE_URL=http://localhost:8080
  PRINT_TOKEN=1
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --email)
      EMAIL="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  echo "Missing required commands: curl and jq"
  exit 1
fi

if [[ -z "$EMAIL" ]]; then
  EMAIL="ragtest_$(date +%s)@example.com"
fi

payload="$(jq -cn --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"

login_response="$(curl -sS -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "$payload")"
token="$(echo "$login_response" | jq -r '.token // empty')"

if [[ -z "$token" ]]; then
  signup_response="$(curl -sS -X POST "$BASE_URL/auth/signup" \
    -H "Content-Type: application/json" \
    -d "$payload")"
  token="$(echo "$signup_response" | jq -r '.token // empty')"
fi

if [[ -z "$token" ]]; then
  echo "Authentication failed."
  echo "Login response: $login_response"
  echo "Signup response: ${signup_response:-<not-called>}"
  exit 1
fi

echo "Auth OK"
echo "EMAIL=$EMAIL"
if [[ "$PRINT_TOKEN" == "1" ]]; then
  echo "TOKEN=$token"
fi
