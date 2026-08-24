#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MOCK_COMPOSE_FILE="${ROOT_DIR}/docker-compose/vnpay-mock.yaml"

export PAYMENT_PROVIDER=VNPAY
export VNPAY_TMN_CODE="${VNPAY_TMN_CODE:-DEMOV210}"
export VNPAY_HASH_SECRET="${VNPAY_HASH_SECRET:-local-demo-secret}"
export VNPAY_PAY_URL="${VNPAY_PAY_URL:-http://localhost:8090/paymentv2/vpcpay.html}"
export VNPAY_API_URL="${VNPAY_API_URL:-http://localhost:8090/merchant_webapi/api/transaction}"
export VNPAY_RETURN_URL="${VNPAY_RETURN_URL:-http://localhost:8080/api/payments/vnpay/return}"
export PAYMENT_FRONTEND_BASE_URL="${PAYMENT_FRONTEND_BASE_URL:-http://localhost:5173}"

docker compose \
  --project-name ecommerce-vnpay-mock \
  --file "${MOCK_COMPOSE_FILE}" \
  up --detach --build

for attempt in $(seq 1 30); do
  if curl --silent --fail http://localhost:8090/health >/dev/null; then
    break
  fi
  if [ "${attempt}" -eq 30 ]; then
    echo "mock VNPAY did not become ready" >&2
    exit 1
  fi
  sleep 1
done

"${SCRIPT_DIR}/start-stack.sh" "$@"

echo "mock VNPAY: http://localhost:8090"
echo "frontend: cd ${ROOT_DIR}/frontend && npm run dev"
