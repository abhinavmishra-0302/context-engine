#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-}"
PASSWORD="${PASSWORD:-Password123}"
FILES_CSV="${FILES_CSV:-}"
QUERY_TEXT="${QUERY_TEXT:-Summarize JVM and garbage collection concepts from all selected docs.}"
FOLLOW_UP_TEXT="${FOLLOW_UP_TEXT:-What about garbage collection details?}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-40}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
STREAM_MAX_SECONDS="${STREAM_MAX_SECONDS:-20}"
TEST_WORKSPACE_QUERY="${TEST_WORKSPACE_QUERY:-1}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/smoke-test.sh [--files /abs/a.txt,/abs/b.txt] [--email you@example.com] [--password Password123]

What this script validates:
  1) Auth (login/signup fallback)
  2) Multi-document upload + processing
  3) POST /query with multiple documentIds + citations
  4) POST /chat/session and POST /chat/message memory flow
  5) Optional workspace query (documentIds omitted)
  6) GET /query/stream SSE token flow

Options:
  --files CSV of absolute file paths (default: auto-generates 2 sample docs in /tmp)
  --email user email (default: generated)
  --password auth password
  --no-workspace-query skip query without documentIds
  --help|-h

Environment overrides:
  BASE_URL=http://localhost:8080
  QUERY_TEXT="Explain topic..."
  FOLLOW_UP_TEXT="Follow-up question..."
  POLL_ATTEMPTS=40
  POLL_INTERVAL_SECONDS=2
  STREAM_MAX_SECONDS=20
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --files)
      FILES_CSV="${2:-}"
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
    --no-workspace-query)
      TEST_WORKSPACE_QUERY="0"
      shift 1
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

wait_ready() {
  local token="$1"
  local doc_id="$2"
  local status=""
  local docs_response=""

  for ((i=1; i<=POLL_ATTEMPTS; i++)); do
    docs_response="$(request GET "$BASE_URL/documents" "" "$token")"
    status="$(echo "$docs_response" | jq -r --arg id "$doc_id" '.[] | select(.documentId == $id) | .status // empty')"
    if [[ "$status" == "READY" ]]; then
      echo "   doc $doc_id READY"
      return 0
    fi
    if [[ "$status" == "FAILED" ]]; then
      echo "Document processing FAILED for $doc_id"
      echo "Documents response: $docs_response"
      return 1
    fi
    echo "   waiting doc $doc_id ($i/$POLL_ATTEMPTS): ${status:-<not-found-yet>}"
    sleep "$POLL_INTERVAL_SECONDS"
  done

  echo "Timed out waiting for READY for $doc_id"
  return 1
}

require_cmd curl
require_cmd jq
require_cmd mktemp
require_cmd timeout

if [[ -z "$EMAIL" ]]; then
  EMAIL="ragtier1_$(date +%s)@example.com"
fi

TMP_DIR="$(mktemp -d /tmp/rag-tier1-smoke-XXXXXX)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

declare -a FILE_PATHS=()

if [[ -n "$FILES_CSV" ]]; then
  IFS=',' read -r -a FILE_PATHS <<< "$FILES_CSV"
else
  cat > "$TMP_DIR/doc_jvm.txt" <<'EOF'
The JVM manages memory using heap and stack regions.
Objects are allocated on heap and referenced by stack frames.
Class metadata is stored in metaspace.
EOF
  cat > "$TMP_DIR/doc_gc.txt" <<'EOF'
Garbage collection identifies unreachable objects and reclaims heap memory.
Common collectors include G1 and ZGC.
Pause time and throughput are important GC tradeoffs.
EOF
  FILE_PATHS=("$TMP_DIR/doc_jvm.txt" "$TMP_DIR/doc_gc.txt")
fi

if [[ "${#FILE_PATHS[@]}" -lt 2 ]]; then
  echo "Provide at least 2 files to validate multi-document retrieval."
  exit 1
fi

for file in "${FILE_PATHS[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "File not found: $file"
    exit 1
  fi
done

echo "==> Base URL: $BASE_URL"
echo "==> Email: $EMAIL"
echo "==> Files:"
for file in "${FILE_PATHS[@]}"; do
  echo "   - $file"
done

login_payload="$(jq -cn --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e,password:$p}')"
login_response="$(request POST "$BASE_URL/auth/login" "$login_payload")"
token="$(echo "$login_response" | jq -r '.token // empty')"

if [[ -z "$token" ]]; then
  echo "==> Login failed (expected for new user). Trying signup..."
  signup_response="$(request POST "$BASE_URL/auth/signup" "$login_payload")"
  token="$(echo "$signup_response" | jq -r '.token // empty')"
fi

if [[ -z "$token" ]]; then
  echo "Auth failed."
  echo "Login response: $login_response"
  echo "Signup response: ${signup_response:-<not-called>}"
  exit 1
fi
echo "==> Auth OK"

declare -a DOC_IDS=()
for file in "${FILE_PATHS[@]}"; do
  upload_response="$(curl -sS -X POST "$BASE_URL/documents/upload" -H "Authorization: Bearer $token" -F "file=@$file")"
  document_id="$(echo "$upload_response" | jq -r '.documentId // empty')"
  if [[ -z "$document_id" ]]; then
    echo "Upload failed for $file"
    echo "Response: $upload_response"
    exit 1
  fi
  DOC_IDS+=("$document_id")
  echo "==> Uploaded: $file -> $document_id"
done

echo "==> Waiting for all documents to be READY..."
for doc_id in "${DOC_IDS[@]}"; do
  wait_ready "$token" "$doc_id"
done

doc_ids_json="$(printf '%s\n' "${DOC_IDS[@]}" | jq -R . | jq -cs .)"

query_payload="$(jq -cn --arg q "$QUERY_TEXT" --argjson ids "$doc_ids_json" '{query:$q,documentIds:$ids}')"
query_response="$(request POST "$BASE_URL/query" "$query_payload" "$token")"
answer="$(echo "$query_response" | jq -r '.answer // empty')"
source_count="$(echo "$query_response" | jq '.sources | length')"

if [[ -z "$answer" || "$answer" == "null" ]]; then
  echo "POST /query failed"
  echo "Response: $query_response"
  exit 1
fi
if [[ "$source_count" -le 0 ]]; then
  echo "POST /query returned no sources"
  echo "Response: $query_response"
  exit 1
fi
echo "==> /query multi-document OK (sources=$source_count)"

missing_fields="$(echo "$query_response" | jq '[.sources[] | select((.documentId == null) or (.documentName == null) or (.chunkId == null) or (.snippet == null))] | length')"
if [[ "$missing_fields" -gt 0 ]]; then
  echo "Citations missing expected fields."
  echo "Response: $query_response"
  exit 1
fi
echo "==> Citation shape OK"

session_response="$(request POST "$BASE_URL/chat/session" "" "$token")"
session_id="$(echo "$session_response" | jq -r '.sessionId // empty')"
if [[ -z "$session_id" ]]; then
  echo "POST /chat/session failed"
  echo "Response: $session_response"
  exit 1
fi
echo "==> Session created: $session_id"

chat_payload="$(jq -cn --arg sid "$session_id" --arg q "$QUERY_TEXT" --argjson ids "$doc_ids_json" '{sessionId:$sid,query:$q,documentIds:$ids}')"
chat_response_1="$(request POST "$BASE_URL/chat/message" "$chat_payload" "$token")"
chat_answer_1="$(echo "$chat_response_1" | jq -r '.answer // empty')"
if [[ -z "$chat_answer_1" ]]; then
  echo "POST /chat/message (turn 1) failed"
  echo "Response: $chat_response_1"
  exit 1
fi

chat_payload_follow_up="$(jq -cn --arg sid "$session_id" --arg q "$FOLLOW_UP_TEXT" --argjson ids "$doc_ids_json" '{sessionId:$sid,query:$q,documentIds:$ids}')"
chat_response_2="$(request POST "$BASE_URL/chat/message" "$chat_payload_follow_up" "$token")"
chat_answer_2="$(echo "$chat_response_2" | jq -r '.answer // empty')"
if [[ -z "$chat_answer_2" ]]; then
  echo "POST /chat/message (turn 2) failed"
  echo "Response: $chat_response_2"
  exit 1
fi
echo "==> Chat memory flow OK (2 turns)"

if [[ "$TEST_WORKSPACE_QUERY" == "1" ]]; then
  workspace_query_payload="$(jq -cn --arg sid "$session_id" --arg q "Summarize all available documents in this workspace." '{query:$q,sessionId:$sid}')"
  workspace_query_response="$(request POST "$BASE_URL/query" "$workspace_query_payload" "$token")"
  workspace_answer="$(echo "$workspace_query_response" | jq -r '.answer // empty')"
  if [[ -z "$workspace_answer" ]]; then
    echo "Workspace query (without documentIds) failed"
    echo "Response: $workspace_query_response"
    exit 1
  fi
  echo "==> Workspace query without documentIds OK"
fi

echo "==> Testing SSE streaming..."
query_encoded="$(jq -rn --arg v "$FOLLOW_UP_TEXT" '$v|@uri')"
stream_url="$BASE_URL/query/stream?query=$query_encoded&sessionId=$session_id"
for id in "${DOC_IDS[@]}"; do
  stream_url="${stream_url}&documentIds=${id}"
done

stream_output_file="$TMP_DIR/stream.out"
set +e
timeout "$STREAM_MAX_SECONDS" curl -sS -N "$stream_url" -H "Authorization: Bearer $token" > "$stream_output_file"
stream_rc=$?
set -e

if [[ "$stream_rc" -ne 0 && "$stream_rc" -ne 124 ]]; then
  echo "SSE call failed with code $stream_rc"
  cat "$stream_output_file"
  exit 1
fi

token_events="$(grep -c '^event: token' "$stream_output_file" || true)"
complete_events="$(grep -c '^event: complete' "$stream_output_file" || true)"
error_events="$(grep -c '^event: error' "$stream_output_file" || true)"
if [[ "$token_events" -le 0 ]]; then
  echo "SSE did not emit token events"
  cat "$stream_output_file"
  exit 1
fi
if [[ "$error_events" -gt 0 ]]; then
  echo "SSE emitted error events"
  cat "$stream_output_file"
  exit 1
fi
echo "==> SSE streaming OK (token_events=$token_events complete_events=$complete_events)"

echo
echo "===== SAMPLE ANSWER (/query) ====="
echo "$answer"
echo "===== SAMPLE CITATIONS ====="
echo "$query_response" | jq '.sources'
echo "===== STREAM PREVIEW ====="
grep -E '^(event:|data:)' "$stream_output_file" | head -n 20 || true
echo
echo "Tier-1 smoke test completed successfully."
