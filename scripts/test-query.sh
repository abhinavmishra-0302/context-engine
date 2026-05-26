#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
QUERY="${QUERY:-Summarize the selected documents.}"
DOCUMENT_IDS_CSV="${DOCUMENT_IDS_CSV:-}"
SESSION_ID="${SESSION_ID:-}"

usage() {
  cat <<'EOF'
Usage:
  TOKEN=<jwt> DOCUMENT_IDS_CSV=<id1,id2> ./scripts/test-query.sh [--query "your question"] [--session-id <uuid>]

Notes:
  - If DOCUMENT_IDS_CSV is empty, query runs across all READY docs for current user.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --query)
      QUERY="${2:-}"
      shift 2
      ;;
    --session-id)
      SESSION_ID="${2:-}"
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

if [[ -n "$DOCUMENT_IDS_CSV" ]]; then
  doc_ids_json="$(printf '%s' "$DOCUMENT_IDS_CSV" | tr ',' '\n' | jq -R . | jq -cs .)"
else
  doc_ids_json="[]"
fi

payload="$(jq -cn \
  --arg q "$QUERY" \
  --arg sid "$SESSION_ID" \
  --argjson ids "$doc_ids_json" \
  'if $sid == "" then
      if ($ids|length) > 0 then {query:$q,documentIds:$ids} else {query:$q} end
    else
      if ($ids|length) > 0 then {query:$q,documentIds:$ids,sessionId:$sid} else {query:$q,sessionId:$sid} end
    end')"

response="$(curl -sS -X POST "$BASE_URL/query" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$payload")"

echo "$response" | jq .
