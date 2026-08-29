#!/usr/bin/env bash
set -euo pipefail

DB_URL="${NETWORK_DB_URL:-postgresql://pavex:pavex@localhost:5436/pavex_network}"

query() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    psql "$DB_URL" -Atqc "$sql"
    return
  fi

  if command -v docker >/dev/null 2>&1 && docker compose ps --status running network-db 2>/dev/null | grep -q network-db; then
    docker compose exec -T network-db psql -U pavex -d pavex_network -Atqc "$sql"
    return
  fi

  echo "Neither local psql nor the running docker compose network-db service is available." >&2
  exit 1
}

assert_eq() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $label: expected $expected, got $actual" >&2
    exit 1
  fi
  echo "PASS $label = $actual"
}

assert_eq "$(query "SELECT COUNT(*) FROM network.regions WHERE code IN ('NORTH','CENTRAL','SOUTH') AND status='ACTIVE';")" "3" "active demo regions"
assert_eq "$(query "SELECT COUNT(*) FROM network.hubs WHERE code IN ('HN-GW','DN-GW','HCM-GW');")" "3" "regional gateways"
assert_eq "$(query "SELECT COUNT(*) FROM network.hubs WHERE code LIKE 'HN-%' OR code LIKE 'DN-%' OR code LIKE 'HCM-%';")" "40" "demo hubs"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_areas WHERE code LIKE 'HN-%' OR code LIKE 'DN-%' OR code LIKE 'HCM-%';")" "37" "demo service areas"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_area_coverages WHERE is_active AND ward_code IS NOT NULL AND province_code IN ('01','48','79');")" "388" "active explicit ward coverages"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_area_coverages WHERE is_active AND ward_code IS NOT NULL AND province_code='01';")" "126" "Hà Nội coverages"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_area_coverages WHERE is_active AND ward_code IS NOT NULL AND province_code='48';")" "94" "Đà Nẵng coverages"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_area_coverages WHERE is_active AND ward_code IS NOT NULL AND province_code='79';")" "168" "Hồ Chí Minh coverages"
assert_eq "$(query "SELECT COUNT(*) FROM network.service_area_coverages WHERE is_active AND tier='ISLAND' AND ward_code IN ('20333','20434','20992','26732','27676');")" "5" "island coverages"

echo
echo "Network demo geography smoke passed."
