#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
SESSION_ID="${SESSION_ID:-}"
DOCUMENT_IDS_CSV="${DOCUMENT_IDS_CSV:-}"
QUERY="${QUERY:-Explain the docs briefly.}"
FOLLOW_UP="${FOLLOW_UP:-Can you expand on garbage collection?}"

usage() {
  cat <<'EOF'
Usage:
  TOKEN=<jwt> DOCUMENT_IDS_CSV=<id1,id2> ./scripts/test-chat.sh [--session-id <uuid>] [--query "..."] [--follow-up "..."]

Behavior:
  - creates session when SESSION_ID not provided
  - sends first and follow-up chat messages
  - prints SESSION_ID for reuse
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --session-id)
      SESSION_ID="${2:-}"
      shift 2
      ;;
    --query)
      QUERY="${2:-}"
      shift 2
      ;;
    --follow-up)
      FOLLOW_UP="${2:-}"
      shift 2
      ;;
    --document-ids)
      DOCUMENT_IDS_CSV="${2:-}"
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
if [[ -z "$TOKEN" ]]; then
  echo "TOKEN is required"
  exit 1
fi

if [[ -z "$SESSION_ID" ]]; then
  session_response="$(curl -sS -X POST "$BASE_URL/chat/session" -H "Authorization: Bearer $TOKEN")"
  SESSION_ID="$(echo "$session_response" | jq -r '.sessionId // empty')"
  if [[ -z "$SESSION_ID" ]]; then
    echo "Failed to create chat session"
    echo "$session_response"
    exit 1
  fi
fi

if [[ -n "$DOCUMENT_IDS_CSV" ]]; then
  doc_ids_json="$(printf '%s' "$DOCUMENT_IDS_CSV" | tr ',' '\n' | jq -R . | jq -cs .)"
else
  doc_ids_json="[]"
fi

send_message() {
  local text="$1"
  local payload
  payload="$(jq -cn --arg sid "$SESSION_ID" --arg q "$text" --argjson ids "$doc_ids_json" \
    'if ($ids|length) > 0 then {sessionId:$sid,query:$q,documentIds:$ids} else {sessionId:$sid,query:$q} end')"
  curl -sS -X POST "$BASE_URL/chat/message" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$payload"
}

echo "SESSION_ID=$SESSION_ID"
echo "Turn 1:"
send_message "$QUERY" | jq .
echo
echo "Turn 2:"
send_message "$FOLLOW_UP" | jq .
