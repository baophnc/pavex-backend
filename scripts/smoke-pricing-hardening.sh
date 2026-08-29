#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-compose.yml}"
DB_SERVICE="${PRICING_DB_SERVICE:-pricing-db}"
DB_USER="${PRICING_DB_USERNAME:-pavex}"
DB_NAME="${PRICING_DB_NAME:-pavex_pricing}"

sql() {
  if command -v psql >/dev/null 2>&1 && PGPASSWORD="${PRICING_DB_PASSWORD:-pavex}" psql -h localhost -p "${PRICING_DB_PORT:-5437}" -U "$DB_USER" -d "$DB_NAME" -Atqc 'select 1' >/dev/null 2>&1; then
    PGPASSWORD="${PRICING_DB_PASSWORD:-pavex}" psql -h localhost -p "${PRICING_DB_PORT:-5437}" -U "$DB_USER" -d "$DB_NAME" -Atqc "$1"
  else
    docker compose -f "$COMPOSE_FILE" exec -T "$DB_SERVICE" psql -U "$DB_USER" -d "$DB_NAME" -Atqc "$1"
  fi
}

expect() {
  local label="$1" expected="$2" query="$3"
  local actual
  actual="$(sql "$query" | tr -d '[:space:]')"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $label: expected $expected, got ${actual:-<empty>}"
    exit 1
  fi
  echo "PASS $label = $actual"
}

expect "pricing audit table" "1" "select count(*) from information_schema.tables where table_schema='pricing' and table_name='audit_logs';"
expect "pricing audit indexes" "3" "select count(*) from pg_indexes where schemaname='pricing' and tablename='audit_logs' and indexname like 'ix_pricing_audit_%';"
expect "quote option primary key available" "1" "select count(*) from information_schema.table_constraints where table_schema='pricing' and table_name='quote_options' and constraint_type='PRIMARY KEY';"
expect "overlapping current active plans" "0" "select greatest(count(*) - 1, 0) from pricing.rate_plans where status='ACTIVE' and effective_from <= now() and (effective_to is null or effective_to > now());"

echo "Pricing lifecycle, audit and shipment-contract storage smoke passed."
