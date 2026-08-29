#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
cd "$repo_dir"

query() {
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${SHIPMENT_DB_PASSWORD:-pavex}" psql \
      "${SHIPMENT_DB_URL_PSQL:-postgresql://pavex@localhost:5438/pavex_shipments}" \
      -Atqc "$1"
  else
    docker compose exec -T shipment-db psql -U pavex -d pavex_shipments -Atqc "$1"
  fi
}

expect() {
  label="$1"
  expected="$2"
  sql="$3"
  actual="$(query "$sql")"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $label: expected $expected, got ${actual:-<empty>}" >&2
    exit 1
  fi
  echo "PASS $label = $actual"
}

expect "shipment schema" "shipments" "SELECT nspname FROM pg_namespace WHERE nspname='shipments'"
expect "shipment table" "1" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='shipments' AND table_name='shipments'"
expect "parcel table" "1" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='shipments' AND table_name='parcels'"
expect "shipment event table" "1" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='shipments' AND table_name='shipment_events'"
expect "shipment outbox table" "1" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='shipments' AND table_name='outbox_events'"
expect "quote single-use constraint" "1" "SELECT COUNT(*) FROM pg_constraint WHERE conname='ux_shipment_quote_id'"
expect "creator idempotency constraint" "1" "SELECT COUNT(*) FROM pg_constraint WHERE conname='ux_shipment_creator_idempotency'"

echo "Shipment foundation smoke passed."
