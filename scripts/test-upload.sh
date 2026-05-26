#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
FILES_CSV="${FILES_CSV:-}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-40}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"

usage() {
  cat <<'EOF'
Usage:
  TOKEN=<jwt> ./scripts/test-upload.sh --files /abs/doc1.txt,/abs/doc2.txt

Output:
  DOCUMENT_IDS_CSV=<id1,id2,...>
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --files)
      FILES_CSV="${2:-}"
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
if [[ -z "$FILES_CSV" ]]; then
  echo "--files is required"
  exit 1
fi

IFS=',' read -r -a raw_paths <<< "$FILES_CSV"
declare -a file_paths=()
for raw in "${raw_paths[@]}"; do
  trimmed="$(echo "$raw" | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')"
  [[ -n "$trimmed" ]] && file_paths+=("$trimmed")
done

if [[ "${#file_paths[@]}" -eq 0 ]]; then
  echo "No valid file paths provided"
  exit 1
fi

for file in "${file_paths[@]}"; do
  [[ -f "$file" ]] || { echo "File not found: $file"; exit 1; }
done

request() {
  local method="$1"
  local url="$2"
  curl -sS -X "$method" "$url" -H "Authorization: Bearer $TOKEN"
}

declare -a doc_ids=()
for file in "${file_paths[@]}"; do
  response="$(curl -sS -X POST "$BASE_URL/documents/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$file")"
  document_id="$(echo "$response" | jq -r '.documentId // empty')"
  if [[ -z "$document_id" ]]; then
    echo "Upload failed: $file"
    echo "$response"
    exit 1
  fi
  doc_ids+=("$document_id")
  echo "Uploaded $file -> $document_id"
done

wait_ready() {
  local id="$1"
  for ((i=1; i<=POLL_ATTEMPTS; i++)); do
    docs="$(request GET "$BASE_URL/documents")"
    status="$(echo "$docs" | jq -r --arg id "$id" '.[] | select(.documentId == $id) | .status // empty')"
    if [[ "$status" == "READY" ]]; then
      echo "READY $id"
      return 0
    fi
    if [[ "$status" == "FAILED" ]]; then
      echo "FAILED $id"
      echo "$docs"
      return 1
    fi
    sleep "$POLL_INTERVAL_SECONDS"
  done
  echo "Timed out: $id"
  return 1
}

for id in "${doc_ids[@]}"; do
  wait_ready "$id"
done

doc_ids_csv="$(IFS=,; echo "${doc_ids[*]}")"
echo "DOCUMENT_IDS_CSV=$doc_ids_csv"
