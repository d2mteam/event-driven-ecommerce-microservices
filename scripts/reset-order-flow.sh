#!/usr/bin/env bash
set -Eeuo pipefail

if [ "${1:-}" != "--yes" ]; then
  echo "This drops all order, payment, reservation, notification and outbox data." >&2
  echo "Run: $0 --yes" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
compose_file="${project_dir}/docker-compose/docker-compose.yaml"
database_name="${FLOW_DB_NAME:-ecommerce_db}"
database_user="${FLOW_DB_USERNAME:-app_user}"
database_password="${FLOW_DB_PASSWORD:-password}"

docker compose -f "${compose_file}" exec -T mariadb \
  mariadb \
  --user="${database_user}" \
  --password="${database_password}" \
  --database="${database_name}" \
  --execute="
    SET FOREIGN_KEY_CHECKS = 0;
    DROP TABLE IF EXISTS notifications;
    DROP TABLE IF EXISTS order_idempotency;
    DROP TABLE IF EXISTS order_outbox_messages;
    DROP TABLE IF EXISTS orders;
    DROP TABLE IF EXISTS payment_outbox_messages;
    DROP TABLE IF EXISTS payments;
    DROP TABLE IF EXISTS inventory_outbox_messages;
    DROP TABLE IF EXISTS inventory_reservations;
    SET FOREIGN_KEY_CHECKS = 1;
    UPDATE inventories SET reserved_quantity = 0;
  "

echo "order flow tables reset; product and on-hand inventory data were kept"
echo "restart the services so Hibernate creates the new worker/outbox schema"
