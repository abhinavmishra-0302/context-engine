#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-}"
PASSWORD="${PASSWORD:-Password123}"
FILE_PATH="${FILE_PATH:-}"
QUERY_TEXT="${QUERY_TEXT:-What more could you add to this document?.}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-30}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/smoke-test.sh --file /absolute/path/to/file.txt [--email you@example.com] [--password Password123]

Environment overrides:
  BASE_URL=http://localhost:8080
  QUERY_TEXT="What is this document about?"
  POLL_ATTEMPTS=30
  POLL_INTERVAL_SECONDS=2
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --file)
      FILE_PATH="${2:-}"
      shift 2
      ;;
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

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1"
    exit 1
  fi
}

require_cmd curl
require_cmd jq

if [[ -z "$EMAIL" ]]; then
  EMAIL="ragtest_$(date +%s)@example.com"
fi

if [[ -z "$FILE_PATH" ]]; then
  FILE_PATH="/tmp/rag-smoke-test.txt"
  cat > "$FILE_PATH" <<'EOF'
RAG smoke test document.
Gemini is the configured model provider for answer generation and embeddings.
This text exists only to validate upload, chunking, embedding, retrieval, and response generation.
EOF
fi

if [[ ! -f "$FILE_PATH" ]]; then
  echo "File not found: $FILE_PATH"
  exit 1
fi

request() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local auth="${4:-}"
  local content_type="${5:-application/json}"

  if [[ -n "$data" && -n "$auth" ]]; then
    curl -sS -X "$method" "$url" -H "Content-Type: $content_type" -H "Authorization: Bearer $auth" -d "$data"
  elif [[ -n "$data" ]]; then
    curl -sS -X "$method" "$url" -H "Content-Type: $content_type" -d "$data"
  elif [[ -n "$auth" ]]; then
    curl -sS -X "$method" "$url" -H "Authorization: Bearer $auth"
  else
    curl -sS -X "$method" "$url"
  fi
}

echo "==> Using API base URL: $BASE_URL"
echo "==> Using email: $EMAIL"
echo "==> Using file: $FILE_PATH"

login_payload="$(jq -cn --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"
login_response="$(request POST "$BASE_URL/auth/login" "$login_payload")"
token="$(echo "$login_response" | jq -r '.token // empty')"

if [[ -z "$token" ]]; then
  echo "==> Login failed or user does not exist. Trying signup..."
  signup_payload="$login_payload"
  signup_response="$(request POST "$BASE_URL/auth/signup" "$signup_payload")"
  token="$(echo "$signup_response" | jq -r '.token // empty')"
fi

if [[ -z "$token" ]]; then
  echo "Failed to get token from login/signup."
  echo "Login response: $login_response"
  echo "Signup response: ${signup_response:-<not-called>}"
  exit 1
fi

echo "==> Auth OK"

upload_response="$(curl -sS -X POST "$BASE_URL/documents/upload" \
  -H "Authorization: Bearer $token" \
  -F "file=@$FILE_PATH")"
document_id="$(echo "$upload_response" | jq -r '.documentId // empty')"

if [[ -z "$document_id" ]]; then
  echo "Upload failed."
  echo "Upload response: $upload_response"
  exit 1
fi

echo "==> Uploaded document: $document_id"
echo "==> Waiting for processing status READY..."

status=""
for ((i=1; i<=POLL_ATTEMPTS; i++)); do
  docs_response="$(request GET "$BASE_URL/documents" "" "$token")"
  status="$(echo "$docs_response" | jq -r --arg id "$document_id" '.[] | select(.documentId == $id) | .status // empty')"
  if [[ "$status" == "READY" ]]; then
    echo "==> Document is READY"
    break
  fi
  if [[ "$status" == "FAILED" ]]; then
    echo "Document processing FAILED."
    echo "Documents response: $docs_response"
    exit 1
  fi
  echo "   attempt $i/$POLL_ATTEMPTS -> status: ${status:-<not-found-yet>}"
  sleep "$POLL_INTERVAL_SECONDS"
done

if [[ "$status" != "READY" ]]; then
  echo "Timed out waiting for READY status."
  exit 1
fi

query_payload="$(jq -cn --arg q "$QUERY_TEXT" --arg id "$document_id" '{query:$q,documentIds:[$id]}')"
query_response="$(request POST "$BASE_URL/query" "$query_payload" "$token")"
answer="$(echo "$query_response" | jq -r '.answer // empty')"

if [[ -z "$answer" ]]; then
  echo "Query failed."
  echo "Query response: $query_response"
  exit 1
fi

echo
echo "===== QUERY ANSWER ====="
echo "$answer"
echo "========================"
echo "Sources: $(echo "$query_response" | jq -c '.sources')"

history_response="$(request GET "$BASE_URL/chat/history" "" "$token")"
history_count="$(echo "$history_response" | jq 'length')"
echo "History items: $history_count"
echo
echo "Smoke test completed successfully."
