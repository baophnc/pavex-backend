#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${NETWORK_DB_NAME:-pavex_network}"
DB_USER="${NETWORK_DB_USERNAME:-pavex}"
DB_PASSWORD="${NETWORK_DB_PASSWORD:-pavex}"
DB_HOST="${NETWORK_DB_HOST:-localhost}"
DB_PORT="${NETWORK_DB_PORT:-5436}"

query() {
  local sql="$1"
  if command -v psql >/dev/null 2>&1; then
    PGPASSWORD="$DB_PASSWORD" psql -At -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$sql"
  else
    docker compose exec -T -e PGPASSWORD="$DB_PASSWORD" network-db \
      psql -At -U "$DB_USER" -d "$DB_NAME" -c "$sql"
  fi
}

assert_eq() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $label: expected $expected, got $actual" >&2
    exit 1
  fi
  echo "PASS $label = $actual"
}

DEMO_LANE_FILTER="(l.code LIKE 'HN-%' OR l.code LIKE 'DN-%' OR l.code LIKE 'HCM-%')"

lanes="$(query "SELECT COUNT(*) FROM network.hub_lanes l WHERE l.status = 'ACTIVE' AND $DEMO_LANE_FILTER;")"
schedules="$(query "SELECT COUNT(*) FROM network.lane_schedules s JOIN network.hub_lanes l ON l.id = s.lane_id WHERE s.status = 'ACTIVE' AND $DEMO_LANE_FILTER;")"
air="$(query "SELECT COUNT(*) FROM network.hub_lanes l WHERE l.status = 'ACTIVE' AND l.transport_mode = 'AIR' AND $DEMO_LANE_FILTER;")"
road="$(query "SELECT COUNT(*) FROM network.hub_lanes l WHERE l.status = 'ACTIVE' AND l.transport_mode = 'ROAD' AND $DEMO_LANE_FILTER;")"
templates="$(query "SELECT COUNT(*) FROM network.route_templates WHERE status = 'ACTIVE' AND code ~ '^(HN|DN|HCM)-(HN|DN|HCM)-(EXPRESS|STANDARD|ECONOMY)$';")"
legs="$(query "SELECT COUNT(*) FROM network.route_template_legs leg JOIN network.route_templates t ON t.id = leg.route_template_id WHERE t.code ~ '^(HN|DN|HCM)-(HN|DN|HCM)-(EXPRESS|STANDARD|ECONOMY)$';")"

assert_eq "active demo lanes" "86" "$lanes"
assert_eq "active demo schedules" "252" "$schedules"
assert_eq "AIR trunk lanes" "6" "$air"
assert_eq "ROAD lanes" "80" "$road"
assert_eq "active demo route templates" "18" "$templates"
assert_eq "demo route template legs" "60" "$legs"

echo
echo "Network demo transport smoke passed."
