#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
QUERY="${QUERY:-Compare G1 and ZGC for low-latency services.}"
SESSION_ID="${SESSION_ID:-}"
DOCUMENT_IDS_CSV="${DOCUMENT_IDS_CSV:-}"
MAX_SECONDS="${MAX_SECONDS:-30}"

usage() {
  cat <<'EOF'
Usage:
  TOKEN=... SESSION_ID=... DOCUMENT_IDS_CSV=id1,id2 ./scripts/test-stream.sh

Env:
  BASE_URL=http://localhost:8080
  QUERY="What about garbage collection?"
  MAX_SECONDS=30
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi

if [[ -z "$TOKEN" ]]; then
  echo "TOKEN is required"
  exit 1
fi
if [[ -z "$SESSION_ID" ]]; then
  echo "SESSION_ID is required"
  exit 1
fi

query_encoded="$(jq -rn --arg v "$QUERY" '$v|@uri')"
url="$BASE_URL/query/stream?query=$query_encoded&sessionId=$SESSION_ID"

if [[ -n "$DOCUMENT_IDS_CSV" ]]; then
  IFS=',' read -r -a doc_ids <<< "$DOCUMENT_IDS_CSV"
  for id in "${doc_ids[@]}"; do
    url="${url}&documentIds=${id}"
  done
fi

echo "Streaming from: $url"
timeout "$MAX_SECONDS" curl -sS -N "$url" -H "Authorization: Bearer $TOKEN" | sed -n '1,160p'
