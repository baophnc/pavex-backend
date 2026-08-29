#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
cd "$repo_dir"

query() {
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="${PRICING_DB_PASSWORD:-pavex}" psql \
      "${PRICING_DB_URL_PSQL:-postgresql://pavex@localhost:5437/pavex_pricing}" \
      -Atqc "$1"
  else
    docker compose exec -T pricing-db psql -U pavex -d pavex_pricing -Atqc "$1"
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

expect "pricing schema" "pricing" "SELECT nspname FROM pg_namespace WHERE nspname='pricing'"
expect "demo active rate plans" "1" "SELECT COUNT(*) FROM pricing.rate_plans WHERE code='DEMO-2026-08' AND status='ACTIVE'"
expect "demo rate rules" "6" "SELECT COUNT(*) FROM pricing.rate_rules r JOIN pricing.rate_plans p ON p.id=r.rate_plan_id WHERE p.code='DEMO-2026-08'"
expect "service levels" "2" "SELECT COUNT(DISTINCT service_level) FROM pricing.rate_rules r JOIN pricing.rate_plans p ON p.id=r.rate_plan_id WHERE p.code='DEMO-2026-08'"
expect "pricing zones" "3" "SELECT COUNT(DISTINCT pricing_zone) FROM pricing.rate_rules r JOIN pricing.rate_plans p ON p.id=r.rate_plan_id WHERE p.code='DEMO-2026-08'"

echo "Pricing foundation smoke passed."
