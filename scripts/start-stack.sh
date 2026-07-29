#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose/docker-compose.yaml"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${RUN_DIR}/logs"
PID_DIR="${RUN_DIR}/pids"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
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

start_service() {
  local dir_name="$1"
  local service_name="$2"
  local port="$3"
  local url="$4"
  local service_dir="${ROOT_DIR}/${dir_name}"
  local log_file="${LOG_DIR}/${service_name}.log"
  local pid_file="${PID_DIR}/${service_name}.pid"
  local java_bin
  local jar_file

  echo "building ${service_name}"
  (
    cd "${service_dir}"
    ./gradlew bootJar >"${log_file}" 2>&1
  )

  jar_file="$(find "${service_dir}/build/libs" -maxdepth 1 -type f -name "*.jar" ! -name "*plain.jar" | head -n 1)"
  if [ -z "${jar_file}" ]; then
    echo "${service_name}: bootJar did not create a runnable jar" >&2
    return 1
  fi

  java_bin="$(java_command)"
  echo "starting ${service_name} on port ${port}"
  : >"${log_file}"
  (
    cd "${service_dir}"
    setsid "${java_bin}" -jar "${jar_file}" >"${log_file}" 2>&1 < /dev/null &
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

"${SCRIPT_DIR}/stop-app-ports.sh" 8081 8082 8083 8084

echo "starting docker infrastructure"
docker compose -f "${COMPOSE_FILE}" up -d mariadb kafka-1 kafka-2 kafka-3 kafka-console

start_service "product-management-service" "product-service" 8081 "http://localhost:8081/api/products"
start_service "inventory-service" "inventory-service" 8083 "http://localhost:8083/internal/inventory/reservations"
start_service "order-service" "order-service" 8082 "http://localhost:8082/api/orders"
start_service "notification-service" "notification-service" 8084 "http://localhost:8084"

echo
echo "stack is ready"
echo "logs: ${LOG_DIR}"
echo "pids: ${PID_DIR}"
echo "redpanda console: http://localhost:8085"
