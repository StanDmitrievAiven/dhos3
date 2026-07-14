#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

: "${AIVEN_OPENSEARCH_HOST:?Set AIVEN_OPENSEARCH_HOST (e.g. in .env)}"
: "${AIVEN_OPENSEARCH_PORT:=443}"
: "${AIVEN_OPENSEARCH_USE_SSL:=true}"

# Prefer Aiven vars by unsetting local OPENSEARCH_HOST if present would win —
# OpenSearchProbeClient prefers OPENSEARCH_* when HOST is set. Force Aiven by clearing local.
unset OPENSEARCH_HOST OPENSEARCH_PORT OPENSEARCH_USE_SSL OPENSEARCH_USERNAME OPENSEARCH_PASSWORD || true
export OPENSEARCH_HOST="$AIVEN_OPENSEARCH_HOST"
export OPENSEARCH_PORT="$AIVEN_OPENSEARCH_PORT"
export OPENSEARCH_USE_SSL="$AIVEN_OPENSEARCH_USE_SSL"
export OPENSEARCH_USERNAME="${AIVEN_OPENSEARCH_USERNAME:-}"
export OPENSEARCH_PASSWORD="${AIVEN_OPENSEARCH_PASSWORD:-}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  fi
fi

cd "$ROOT/spike/java"
./gradlew --quiet run
