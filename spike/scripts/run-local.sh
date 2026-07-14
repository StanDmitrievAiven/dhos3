#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export OPENSEARCH_HOST="${OPENSEARCH_HOST:-localhost}"
export OPENSEARCH_PORT="${OPENSEARCH_PORT:-9200}"
export OPENSEARCH_USE_SSL="${OPENSEARCH_USE_SSL:-false}"

# Prefer JDK 21 (required by OpenSearch 3 client toolchain)
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
  fi
fi

"$ROOT/spike/scripts/wait-for-os.sh" >/dev/null
cd "$ROOT/spike/java"
./gradlew --quiet run
