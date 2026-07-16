#!/usr/bin/env bash
# Build DataHub debug images from vendor/datahub (OS3 shim branch) and run the
# debug compose profile with search pointed at Aiven OpenSearch 3.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DH="${ROOT}/vendor/datahub"
OVERRIDE="${ROOT}/docker/aiven-os3.override.yml"
COMPOSE_DIR="${DH}/docker/profiles"
PROJECT="${COMPOSE_PROJECT_NAME:-datahub}"
# debug-min = GMS + frontend + upgrade (no datahub-actions / Python packaging)
PROFILE="${DATAHUB_COMPOSE_PROFILE:-debug-min}"
GRADLE_BUILD_TASK="${DATAHUB_GRADLE_BUILD_TASK:-buildImagesquickstartDebugMin}"
JAVA_HOME_DEFAULT="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"

usage() {
  cat <<EOF
Usage: $(basename "$0") [build|up|down|logs|status|smoke]

  build   Build debug Docker images only (long)
  up      Build (if needed) + start debug stack vs Aiven OS3
  down    Stop the compose project (keeps volumes)
  logs    Tail GMS / system-update logs
  status  docker compose ps
  smoke   Hit GMS health + cluster-aware search ping via GraphQL login page
EOF
}

need_env() {
  if [[ ! -f "${ROOT}/.env" ]]; then
    echo "Missing ${ROOT}/.env — copy .env.example and fill AIVEN_OPENSEARCH_*" >&2
    exit 1
  fi
  set -a
  # shellcheck disable=SC1091
  source "${ROOT}/.env"
  set +a
  : "${AIVEN_OPENSEARCH_HOST:?Set AIVEN_OPENSEARCH_HOST in .env}"
  : "${AIVEN_OPENSEARCH_USERNAME:?Set AIVEN_OPENSEARCH_USERNAME in .env}"
  : "${AIVEN_OPENSEARCH_PASSWORD:?Set AIVEN_OPENSEARCH_PASSWORD in .env}"
  export AIVEN_OPENSEARCH_PORT="${AIVEN_OPENSEARCH_PORT:-443}"
  export AIVEN_OPENSEARCH_USE_SSL="${AIVEN_OPENSEARCH_USE_SSL:-true}"
}

ensure_java() {
  export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
  if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "JAVA_HOME not usable: ${JAVA_HOME}" >&2
    exit 1
  fi
  export PATH="${JAVA_HOME}/bin:${PATH}"
}

ensure_token_secrets() {
  local secrets_gradle="${DH}/docker/.local-secrets.env"
  local secrets_cli="${HOME}/.datahub/quickstart/.local-secrets.env"
  local secrets=""
  for candidate in "$secrets_gradle" "$secrets_cli"; do
    if [[ -f "$candidate" ]]; then
      secrets="$candidate"
      break
    fi
  done
  if [[ -n "$secrets" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$secrets"
    set +a
  fi
  if [[ -z "${DATAHUB_TOKEN_SERVICE_SIGNING_KEY:-}" || -z "${DATAHUB_TOKEN_SERVICE_SALT:-}" ]]; then
    secrets="${secrets_gradle}"
    mkdir -p "$(dirname "$secrets")"
    DATAHUB_TOKEN_SERVICE_SIGNING_KEY="$(openssl rand -base64 32)"
    DATAHUB_TOKEN_SERVICE_SALT="$(openssl rand -base64 32)"
    cat >"$secrets" <<EOF
# Auto-generated local dev secrets — do not commit
DATAHUB_TOKEN_SERVICE_SIGNING_KEY=${DATAHUB_TOKEN_SERVICE_SIGNING_KEY}
DATAHUB_TOKEN_SERVICE_SALT=${DATAHUB_TOKEN_SERVICE_SALT}
EOF
    echo "Wrote token secrets to ${secrets}"
  fi
  export DATAHUB_TOKEN_SERVICE_SIGNING_KEY DATAHUB_TOKEN_SERVICE_SALT
}

compose() {
  (
    cd "${COMPOSE_DIR}"
    docker compose -p "${PROJECT}" \
      -f docker-compose.yml \
      -f "${OVERRIDE}" \
      --profile "${PROFILE}" \
      "$@"
  )
}

cmd_build() {
  need_env
  ensure_java
  echo "==> Building debug images from ${DH} ($(git -C "${DH}" rev-parse --short HEAD))"
  (cd "${DH}" && ./gradlew ":docker:${GRADLE_BUILD_TASK}")
}

free_port_conflicts() {
  # Local spike OS3 publishes :9200; stub no longer needs it, but stop to free RAM.
  if docker ps --format '{{.Names}}' | grep -qx 'dhos3-opensearch'; then
    echo "==> Stopping dhos3-opensearch (local spike) to free resources"
    docker stop dhos3-opensearch >/dev/null || true
  fi
}

cmd_up() {
  need_env
  ensure_java
  ensure_token_secrets
  free_port_conflicts

  if ! docker image inspect "acryldata/datahub-gms:debug" >/dev/null 2>&1; then
    cmd_build
  else
    echo "==> Found acryldata/datahub-gms:debug — rebuild with: $0 build"
  fi

  export DATAHUB_VERSION="${DATAHUB_VERSION:-debug}"
  export DATAHUB_TELEMETRY_ENABLED=false
  export DEV_TOOLING_ENABLED=true

  echo "==> Starting debug profile → Aiven ${AIVEN_OPENSEARCH_HOST}:${AIVEN_OPENSEARCH_PORT} (OPENSEARCH_3)"
  compose up -d --remove-orphans
  echo "==> Waiting for GMS health on :8080 ..."
  for i in $(seq 1 90); do
    if curl -sf "http://localhost:${DATAHUB_MAPPED_GMS_PORT:-8080}/health" >/dev/null; then
      echo "GMS healthy (check ${i})"
      compose ps
      echo
      echo "UI:  http://localhost:${DATAHUB_MAPPED_FRONTEND_PORT:-9002}"
      echo "GMS: http://localhost:${DATAHUB_MAPPED_GMS_PORT:-8080}"
      return 0
    fi
    sleep 10
  done
  echo "GMS did not become healthy in time — check: $0 logs" >&2
  compose ps
  exit 1
}

cmd_down() {
  need_env
  ensure_token_secrets
  compose down --remove-orphans
}

cmd_logs() {
  case "${PROFILE}" in
    debug-min)
      compose logs -f --tail=200 system-update-debug datahub-gms-debug-min frontend-debug-min
      ;;
    *)
      compose logs -f --tail=200 system-update-debug datahub-gms-debug frontend-debug
      ;;
  esac
}

cmd_status() {
  compose ps
}

cmd_smoke() {
  local gms="http://localhost:${DATAHUB_MAPPED_GMS_PORT:-8080}"
  local ui="http://localhost:${DATAHUB_MAPPED_FRONTEND_PORT:-9002}/"
  echo "==> GET ${gms}/health"
  code="$(curl -s -o /dev/null -w '%{http_code}' "${gms}/health")"
  echo "health HTTP ${code}"
  [[ "${code}" == "200" ]] || { echo "GMS health failed" >&2; exit 1; }
  echo "==> UI ${ui}"
  curl -sf -o /dev/null -w "frontend HTTP %{http_code}\n" "${ui}"
  echo "==> GMS shim log (OPENSEARCH_3)"
  docker logs datahub-datahub-gms-debug-min-1 2>&1 | rg -m1 'Created OpenSearch 3.x shim' || true
}

main() {
  local cmd="${1:-up}"
  case "$cmd" in
    -h|--help|help) usage ;;
    build) cmd_build ;;
    up) cmd_up ;;
    down) cmd_down ;;
    logs) cmd_logs ;;
    status) cmd_status ;;
    smoke) cmd_smoke ;;
    *) usage; exit 1 ;;
  esac
}

main "$@"
