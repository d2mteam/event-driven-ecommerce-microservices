#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

ORDER_URL="${ORDER_URL:-http://localhost:8080/api/orders}"
PRODUCT_ID_1="${PRODUCT_ID_1:-1}"
PRODUCT_ID_2="${PRODUCT_ID_2:-2}"
QTY_1="${QTY_1:-2}"
QTY_2="${QTY_2:-1}"
USER_ID="${USER_ID:-11111111-1111-1111-1111-111111111111}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-demo-order-$(date +%s%N)}"
DB_CONTAINER="${DB_CONTAINER:-ecommerce_mariadb}"
DB_NAME="${DB_NAME:-ecommerce_db}"
DB_USER="${DB_USER:-app_user}"
DB_PASSWORD="${DB_PASSWORD:-password}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

sql_value() {
  local query="$1"
  docker exec "${DB_CONTAINER}" mariadb \
    -u"${DB_USER}" \
    -p"${DB_PASSWORD}" \
    --batch \
    --skip-column-names \
    "${DB_NAME}" \
    -e "${query}"
}

wait_until() {
  local label="$1"
  local query="$2"
  local expected="$3"
  local attempts="${4:-40}"

  for attempt in $(seq 1 "${attempts}"); do
    value="$(sql_value "${query}" | tail -n 1 | tr -d '\r' || true)"
    if [ "${value}" = "${expected}" ]; then
      echo "${label}: ${value}"
      return 0
    fi
    sleep 1
  done

  echo "${label}: expected ${expected}, got ${value:-<empty>}" >&2
  return 1
}

require_command curl
require_command jq
require_command docker

before_stock_1="$(sql_value "select on_hand_quantity from inventories where product_id = ${PRODUCT_ID_1};" | tail -n 1 | tr -d '\r')"
before_stock_2="$(sql_value "select on_hand_quantity from inventories where product_id = ${PRODUCT_ID_2};" | tail -n 1 | tr -d '\r')"

request_body="$(jq -n \
  --argjson productId1 "${PRODUCT_ID_1}" \
  --argjson productId2 "${PRODUCT_ID_2}" \
  --argjson qty1 "${QTY_1}" \
  --argjson qty2 "${QTY_2}" \
  '{items: [
    {productId: $productId1, quantity: $qty1},
    {productId: $productId2, quantity: $qty2}
  ]}')"

response_file="$(mktemp)"
trap 'rm -f "${response_file}"' EXIT
status="$(curl -sS \
  -o "${response_file}" \
  -w "%{http_code}" \
  -X POST "${ORDER_URL}" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: ${USER_ID}" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${request_body}")"

if [ "${status}" != "201" ]; then
  echo "order request failed with HTTP ${status}" >&2
  cat "${response_file}" >&2
  exit 1
fi

order_id="$(jq -r '.id' "${response_file}")"
reservation_id="$(jq -r '.reservationId' "${response_file}")"
total_price="$(jq -r '.totalPrice' "${response_file}")"
order_status="$(jq -r '.status' "${response_file}")"

if [ "${order_status}" != "CONFIRMED" ]; then
  echo "expected order status CONFIRMED, got ${order_status}" >&2
  exit 1
fi

echo "order created: ${order_id}"
echo "reservation: ${reservation_id}"
echo "order status: ${order_status}"
echo "total price: ${total_price}"

wait_until \
  "outbox published" \
  "select count(*) from order_outbox_messages where message_key = '${order_id}' and topic = 'order.events' and published_at is not null;" \
  "1"

wait_until \
  "event version" \
  "select json_unquote(json_extract(payload, '$.eventVersion')) from order_outbox_messages where message_key = '${order_id}' and topic = 'order.events' order by id desc limit 1;" \
  "2"

wait_until \
  "reservation settled" \
  "select status from inventory_reservations where id = ${reservation_id};" \
  "SETTLED"

wait_until \
  "notification created" \
  "select count(*) from notifications where order_id = '${order_id}' and sent = 1;" \
  "1"

after_stock_1="$(sql_value "select on_hand_quantity from inventories where product_id = ${PRODUCT_ID_1};" | tail -n 1 | tr -d '\r')"
after_stock_2="$(sql_value "select on_hand_quantity from inventories where product_id = ${PRODUCT_ID_2};" | tail -n 1 | tr -d '\r')"
expected_stock_1="$((before_stock_1 - QTY_1))"
expected_stock_2="$((before_stock_2 - QTY_2))"

if [ "${after_stock_1}" != "${expected_stock_1}" ]; then
  echo "stock check failed for product ${PRODUCT_ID_1}: expected ${expected_stock_1}, got ${after_stock_1}" >&2
  exit 1
fi

if [ "${after_stock_2}" != "${expected_stock_2}" ]; then
  echo "stock check failed for product ${PRODUCT_ID_2}: expected ${expected_stock_2}, got ${after_stock_2}" >&2
  exit 1
fi

echo "stock product ${PRODUCT_ID_1}: ${before_stock_1} -> ${after_stock_1}"
echo "stock product ${PRODUCT_ID_2}: ${before_stock_2} -> ${after_stock_2}"
echo "order flow OK"
