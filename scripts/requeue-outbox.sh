#!/usr/bin/env bash
set -Eeuo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <order|payment|inventory> <message-id>" >&2
  exit 1
fi

outbox_name="$1"
message_id="$2"

case "${outbox_name}" in
  order)
    table_name="order_outbox_messages"
    ;;
  payment)
    table_name="payment_outbox_messages"
    ;;
  inventory)
    table_name="inventory_outbox_messages"
    ;;
  *)
    echo "unknown outbox: ${outbox_name}" >&2
    exit 1
    ;;
esac

if ! [[ "${message_id}" =~ ^[1-9][0-9]*$ ]]; then
  echo "message-id must be a positive integer" >&2
  exit 1
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
compose_file="${project_dir}/docker-compose/docker-compose.yaml"
database_name="${OUTBOX_DB_NAME:-ecommerce_db}"
database_user="${OUTBOX_DB_USERNAME:-app_user}"
database_password="${OUTBOX_DB_PASSWORD:-password}"

updated_rows="$(
  docker compose -f "${compose_file}" exec -T mariadb \
    mariadb \
    --user="${database_user}" \
    --password="${database_password}" \
    --database="${database_name}" \
    --batch \
    --skip-column-names \
    --execute="
      UPDATE ${table_name}
      SET status = 'PENDING',
          next_attempt_at = CURRENT_TIMESTAMP(6),
          lock_token = NULL,
          locked_until = NULL,
          last_error = NULL
      WHERE id = ${message_id}
        AND status = 'FAILED';
      SELECT ROW_COUNT();
    "
)"

if [ "${updated_rows}" = "1" ]; then
  echo "requeued ${outbox_name} outbox message ${message_id}"
else
  echo "no FAILED ${outbox_name} outbox message found with id ${message_id}" >&2
  exit 2
fi
