#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
K6_DIR="${ROOT_DIR}/scripts/k6"
TEST_SUITE="${1:-all}"
ORDER_URL="${ORDER_URL:-http://localhost:8080/api/orders}"

run_test() {
  local file_name="$1"

  echo "running ${file_name}"
  k6 run \
    -e "ORDER_URL=${ORDER_URL}" \
    "${K6_DIR}/${file_name}"
}

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6 is not installed" >&2
  exit 1
fi

case "${TEST_SUITE}" in
  happy)
    run_test "happy-orders.js"
    ;;
  unhappy)
    run_test "unhappy-orders.js"
    ;;
  capacity)
    echo "warning: capacity test creates real orders and consumes inventory"
    run_test "capacity-orders.js"
    ;;
  all)
    run_test "happy-orders.js"
    run_test "unhappy-orders.js"
    ;;
  *)
    echo "usage: $0 [all|happy|unhappy|capacity]" >&2
    exit 2
    ;;
esac
