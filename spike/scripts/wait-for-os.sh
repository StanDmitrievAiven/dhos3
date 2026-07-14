#!/usr/bin/env bash
set -euo pipefail

HOST="${OPENSEARCH_HOST:-localhost}"
PORT="${OPENSEARCH_PORT:-9200}"
URL="http://${HOST}:${PORT}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-60}"

echo "Waiting for OpenSearch at ${URL} ..."
for i in $(seq 1 "${MAX_ATTEMPTS}"); do
  if curl -sf "${URL}" >/dev/null 2>&1; then
    echo "OpenSearch is up (attempt ${i})"
    curl -s "${URL}" | python3 -m json.tool 2>/dev/null || curl -s "${URL}"
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for OpenSearch after ${MAX_ATTEMPTS} attempts" >&2
exit 1
