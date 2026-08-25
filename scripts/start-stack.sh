#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose/docker-compose.yaml"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${RUN_DIR}/logs"
PID_DIR="${RUN_DIR}/pids"
OTEL_ENABLED=false
OTEL_JAVAAGENT_VERSION="${OTEL_JAVAAGENT_VERSION:-2.30.0}"
OTEL_JAVAAGENT_PATH="${OTEL_JAVAAGENT_PATH:-${RUN_DIR}/otel/opentelemetry-javaagent.jar}"
JVM_INITIAL_HEAP="${JVM_INITIAL_HEAP:-64m}"
JVM_SMALL_MAX_HEAP="${JVM_SMALL_MAX_HEAP:-256m}"
JVM_LARGE_MAX_HEAP="${JVM_LARGE_MAX_HEAP:-384m}"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

usage() {
  cat <<'EOF'
Usage: ./scripts/start-stack.sh [options]

Options:
  --otel [agent.jar]  Enable tracing for every service and start Collector + Zipkin.
                      Without a path, the Java agent is cached under .run/otel.
  --no-otel           Disable OpenTelemetry (default).
  -h, --help          Show this help.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --otel)
      OTEL_ENABLED=true
      if [ "$#" -gt 1 ] && [[ "$2" != -* ]]; then
        OTEL_JAVAAGENT_PATH="$2"
        shift
      fi
      ;;
    --no-otel)
      OTEL_ENABLED=false
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
  shift
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

prepare_otel_agent() {
  if [ -f "${OTEL_JAVAAGENT_PATH}" ]; then
    return 0
  fi

  local agent_dir
  local download_file
  local download_url
  agent_dir="$(dirname "${OTEL_JAVAAGENT_PATH}")"
  download_file="${OTEL_JAVAAGENT_PATH}.download"
  download_url="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar"

  mkdir -p "${agent_dir}"
  echo "downloading OpenTelemetry Java agent ${OTEL_JAVAAGENT_VERSION}"
  curl --fail --location --retry 3 \
    --output "${download_file}" \
    "${download_url}"
  mv "${download_file}" "${OTEL_JAVAAGENT_PATH}"
}

java_command() {
  if [ -n "${JAVA_HOME:-}" ]; then
    echo "${JAVA_HOME}/bin/java"
    return 0
  fi
  echo "java"
}

wait_http() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-60}"

  for attempt in $(seq 1 "${max_attempts}"); do
    status="$(curl -sS -o /dev/null -w "%{http_code}" --max-time 2 "${url}" 2>/dev/null || true)"
    if [ "${status}" != "000" ]; then
      echo "${name}: ready (${status})"
      return 0
    fi
    sleep 1
  done

  echo "${name}: not ready at ${url}" >&2
  return 1
}

# minio-init tạo bucket rồi thoát. product-service kiểm bucket ngay lúc khởi
# động (verifyBucket) nên phải đợi nó chạy xong, không chỉ đợi minio healthy.
wait_minio_bucket() {
  local state

  for _ in $(seq 1 60); do
    state="$(docker inspect -f '{{.State.Status}}:{{.State.ExitCode}}' \
      ecommerce_minio_init 2>/dev/null || true)"
    case "${state}" in
      exited:0)
        echo "minio-init: bucket ready"
        return 0
        ;;
      exited:*)
        echo "minio-init: failed (${state})" >&2
        docker logs ecommerce_minio_init 2>&1 | tail -n 20 >&2
        return 1
        ;;
    esac
    sleep 1
  done

  echo "minio-init: did not finish in time" >&2
  return 1
}

start_service() {
  local dir_name="$1"
  local service_name="$2"
  local port="$3"
  local url="$4"
  local max_heap="$5"
  local service_dir="${ROOT_DIR}/${dir_name}"
  local log_file="${LOG_DIR}/${service_name}.log"
  local pid_file="${PID_DIR}/${service_name}.pid"
  local java_bin
  local jar_file
  local -a java_args=(
    "-Xms${JVM_INITIAL_HEAP}"
    "-Xmx${max_heap}"
  )
  local -a runtime_environment=()

  echo "building ${service_name}"
  (
    cd "${service_dir}"
    ./gradlew --no-daemon bootJar >"${log_file}" 2>&1
  )

  jar_file="$(find "${service_dir}/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*plain.jar" | head -n 1)"
  if [ -z "${jar_file}" ]; then
    echo "${service_name}: bootJar did not create a runnable jar" >&2
    return 1
  fi

  java_bin="$(java_command)"
  if [ "${OTEL_ENABLED}" = true ]; then
    java_args+=("-javaagent:${OTEL_JAVAAGENT_PATH}")
    runtime_environment+=(
      "OTEL_SERVICE_NAME=${service_name}"
      "OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4318}"
      "OTEL_EXPORTER_OTLP_PROTOCOL=${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}"
      "OTEL_TRACES_EXPORTER=${OTEL_TRACES_EXPORTER:-otlp}"
      "OTEL_METRICS_EXPORTER=${OTEL_METRICS_EXPORTER:-none}"
      "OTEL_LOGS_EXPORTER=${OTEL_LOGS_EXPORTER:-none}"
    )
  fi
  echo "starting ${service_name} on port ${port}"
  : >"${log_file}"
  (
    cd "${service_dir}"
    setsid env "${runtime_environment[@]}" \
      "${java_bin}" "${java_args[@]}" -jar "${jar_file}" \
      >"${log_file}" 2>&1 < /dev/null &
    echo "$!" >"${pid_file}"
  )

  wait_http "${service_name}" "${url}" 90 || {
    echo "last log lines from ${log_file}:"
    tail -n 80 "${log_file}" || true
    return 1
  }
}

require_command docker
require_command curl
require_command lsof
require_command setsid

if [ "${OTEL_ENABLED}" = true ]; then
  prepare_otel_agent
fi

"${SCRIPT_DIR}/stop-app-ports.sh" 8080 8081 8082 8083 8084 8086 8087

echo "starting docker infrastructure"
infrastructure_services=(
  mariadb cloudbeaver redis redis-insight
  kafka-1 kafka-console
  minio minio-init mailhog
)
compose_options=(-f "${COMPOSE_FILE}")

if [ "${OTEL_ENABLED}" = true ]; then
  compose_options+=(--profile observability)
  infrastructure_services+=(zipkin otel-collector)
else
  docker compose -f "${COMPOSE_FILE}" --profile observability \
    stop otel-collector zipkin >/dev/null
fi

docker compose "${compose_options[@]}" up -d --remove-orphans \
  "${infrastructure_services[@]}"

if [ "${OTEL_ENABLED}" = true ]; then
  wait_http "zipkin" "http://localhost:9411/health" 60
  wait_http "otel-collector" "http://localhost:13133" 60
fi

wait_minio_bucket
# notification-service chỉ chạm SMTP lúc gửi, không phải lúc khởi động, nên
# đợi ở đây chỉ để lỗi lộ ra ngay thay vì lặng lẽ ở lần gửi mail đầu tiên.
wait_http "mailhog" "http://localhost:8025/api/v2/messages" 30

start_service \
  "product-management-service" "product-service" \
  8081 "http://localhost:8081/api/products" "${JVM_SMALL_MAX_HEAP}"
start_service \
  "inventory-service" "inventory-service" \
  8083 "http://localhost:8083/internal/inventory/reservations" "${JVM_LARGE_MAX_HEAP}"
start_service \
  "payment-gateway" "payment-gateway" \
  8086 "http://localhost:8086/api/payments/0" "${JVM_SMALL_MAX_HEAP}"
start_service \
  "order-service" "order-service" \
  8082 "http://localhost:8082/api/orders" "${JVM_LARGE_MAX_HEAP}"
start_service \
  "notification-service" "notification-service" \
  8084 "http://localhost:8084" "${JVM_SMALL_MAX_HEAP}"
start_service \
  "user-service" "user-service" \
  8087 "http://localhost:8087/api/auth/login" "${JVM_SMALL_MAX_HEAP}"
start_service \
  "api-gateway" "api-gateway" \
  8080 "http://localhost:8080/actuator/health" "${JVM_LARGE_MAX_HEAP}"

echo
echo "stack is ready"
echo "logs: ${LOG_DIR}"
echo "pids: ${PID_DIR}"
echo "api gateway: http://localhost:8080"
echo "redpanda console: http://localhost:8085"
echo "redis insight: http://localhost:5540"
echo "database ui: http://localhost:8978 (admin / admin123)"
echo "minio console: http://localhost:9011 (minioadmin / minioadmin)"
echo "mailhog inbox: http://localhost:8025"
echo "mock payment: http://localhost:8080/api/payments/{paymentId}/mock"
if [ "${OTEL_ENABLED}" = true ]; then
  echo "otel traces: enabled -> ${OTEL_EXPORTER_OTLP_ENDPOINT:-http://localhost:4318}"
  echo "zipkin: http://localhost:9411/zipkin"
else
  echo "otel traces: disabled (use --otel)"
fi
