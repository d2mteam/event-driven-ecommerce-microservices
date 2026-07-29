#!/usr/bin/env bash
set -Eeuo pipefail

PORTS=("$@")
if [ "${#PORTS[@]}" -eq 0 ]; then
  PORTS=(8081 8082 8083 8084)
fi

for port in "${PORTS[@]}"; do
  pids="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -z "${pids}" ]; then
    echo "port ${port}: free"
    continue
  fi

  echo "port ${port}: stopping pid(s) ${pids//$'\n'/ }"
  kill ${pids} 2>/dev/null || true
done

sleep 2

for port in "${PORTS[@]}"; do
  pids="$(lsof -tiTCP:"${port}" -sTCP:LISTEN 2>/dev/null || true)"
  if [ -n "${pids}" ]; then
    echo "port ${port}: force stopping pid(s) ${pids//$'\n'/ }"
    kill -9 ${pids} 2>/dev/null || true
  fi
done
