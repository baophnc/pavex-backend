#!/usr/bin/env bash
# Replaces the data of the running ./pavex stack with the nationwide dataset (scripts/seed-full/generate.py):
# 3 regional centres, one provincial post office per province, one post office per ward, the lanes between them
# and ~10,700 staff accounts. No shops, customers or shipments: create them by hand.
#
#   ./pavex seed full            (or scripts/seed-full/apply.sh)
#
# 1. backs up every database to .local/backups/<time>-seed-full/
# 2. stops the Java services (databases, queues and the portals keep running)
# 3. writes the network, the accounts and the workforce, and clears shops, shipments, quotes, cash, invoices,
#    trips and notifications; admin@pavex.com and your own accounts (e.g. Gmail) stay, without the MERCHANT role
# 4. starts the services again and checks the result; Operations then rosters the shifts
#
# Every @pavex.test account signs in with the seed-mini password (scripts/seed-mini/README.md).
# The full staff list is written to .local/seed/full/staff.csv. `./pavex reset` goes back to seed-mini.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
pavex="$repo_dir/pavex"
out_dir="$repo_dir/.local/seed/full"
java_services=(discovery-server api-gateway identity-service auth-service partners-service network-service
  pricing-service shipment-service operations-service billing-service fleet-service notifications-service)

postgres="$("$pavex" compose ps -q postgres 2>/dev/null || true)"
[[ -n "$postgres" ]] || { echo "The ./pavex stack is not running: start it with ./pavex up first." >&2; exit 1; }
rabbitmq="$("$pavex" compose ps -q rabbitmq)"
password="$(sed -n 's/^SEED_PASSWORD="\(.*\)"$/\1/p' "$repo_dir/scripts/seed-mini/apply.sh")"
[[ -n "$password" ]] || { echo "SEED_PASSWORD not found in scripts/seed-mini/apply.sh" >&2; exit 1; }

psql_in() { docker exec -i -e PGOPTIONS="-c client_min_messages=warning" "$postgres" psql -q -U postgres -v ON_ERROR_STOP=1 -d "$1" "${@:2}"; }
query() { docker exec "$postgres" psql -tA -U postgres -d "$1" -c "$2"; }
truncate_schema() {
  psql_in "$1" <<SQL
DO \$\$ DECLARE t text; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = '$2' AND tablename NOT LIKE 'flyway%' ${3:-} LOOP
    EXECUTE format('TRUNCATE $2.%I CASCADE', t);
  END LOOP;
END \$\$;
SQL
}

backup_dir="$repo_dir/.local/backups/$(date +%Y%m%d-%H%M%S)-seed-full"
mkdir -p "$backup_dir" "$out_dir/catalog"
echo "==> Backing up the databases to ${backup_dir#"$repo_dir"/}"
for db in auth identity partners network pricing operations shipments billing fleet notifications; do
  docker exec "$postgres" pg_dump -U postgres -Fc "pavex_$db" >"$backup_dir/$db.dump"
done

echo "==> Generating the dataset"
query pavex_partners "COPY (SELECT code, name FROM partners.provinces ORDER BY code) TO STDOUT" >"$out_dir/catalog/provinces.tsv"
query pavex_partners "COPY (SELECT code, province_code, name, full_name, code_name FROM partners.wards ORDER BY province_code, code) TO STDOUT" \
  >"$out_dir/catalog/wards.tsv"
python3 "$script_dir/generate.py" --catalog "$out_dir/catalog" --out "$out_dir"

echo "==> Stopping the Java services"
"$pavex" compose stop "${java_services[@]}" >/dev/null

echo "==> Writing the network, accounts and workforce"
psql_in pavex_network <"$out_dir/network.sql"
psql_in pavex_identity <"$out_dir/identity.sql"
# Credentials of every @pavex.test account, with the one test password.
psql_in postgres -c "CREATE EXTENSION IF NOT EXISTS pgcrypto"
hash="{bcrypt}$(echo "SELECT crypt(:'pw', gen_salt('bf', 10))" | psql_in postgres -tA -v pw="$password")"
query pavex_identity "COPY (SELECT id, email FROM identity.user_account WHERE email LIKE '%@pavex.test') TO STDOUT" \
  >"$out_dir/seed-users.tsv"
{
  echo "BEGIN;"
  echo "DELETE FROM auth.user_account WHERE email LIKE '%@pavex.test' OR email LIKE '%@demo.pavex.local'"
  echo "  OR email LIKE '%@khachhang.pavex.com' OR (email LIKE '%@pavex.com' AND email <> 'admin@pavex.com');"
  echo "CREATE TEMP TABLE seed_user (id uuid, email text) ON COMMIT DROP;"
  echo "COPY seed_user (id, email) FROM stdin;"
  cat "$out_dir/seed-users.tsv"
  echo '\.'
  echo "INSERT INTO auth.user_account (id, email, password_hash, email_verified, failed_login_attempts, created_at,"
  echo "  updated_at, version, identity_provisioned, must_change_password)"
  echo "SELECT id, email, :'pwhash', TRUE, 0, NOW(), NOW(), 0, TRUE, FALSE FROM seed_user;"
  echo "DELETE FROM auth.refresh_session WHERE user_id NOT IN (SELECT id FROM auth.user_account);"
  echo "COMMIT;"
} | psql_in pavex_auth -v pwhash="$hash"
psql_in pavex_operations <"$out_dir/operations.sql"

echo "==> Clearing shops, shipments, quotes, cash, invoices, trips and notifications"
# Partners cannot read Identity, so the ids of the accounts that still exist are passed in.
query pavex_identity "COPY (SELECT id FROM identity.user_account) TO STDOUT" >"$out_dir/live-users.txt"
{
  echo "BEGIN;"
  echo "TRUNCATE partners.merchants, partners.merchant_role_outbox CASCADE;"
  echo "SELECT setval('partners.merchant_code_seq', 1, false);"
  echo "CREATE TEMP TABLE live_users (id uuid PRIMARY KEY) ON COMMIT DROP;"
  echo "COPY live_users (id) FROM stdin;"
  cat "$out_dir/live-users.txt"
  echo '\.'
  echo "DELETE FROM partners.user_addresses WHERE user_id NOT IN (SELECT id FROM live_users);"
  echo "COMMIT;"
} | psql_in pavex_partners
truncate_schema pavex_shipments shipments
truncate_schema pavex_billing billing
truncate_schema pavex_fleet fleet
truncate_schema pavex_notifications notifications
psql_in pavex_pricing -c "TRUNCATE pricing.quote_requests CASCADE"
for queue in billing.shipment-events billing.shipment-events.dlq fleet.shipment-events fleet.shipment-events.dlq \
  notifications.events notifications.events.dlq; do
  docker exec "$rabbitmq" rabbitmqctl -q -p pavex purge_queue "$queue" >/dev/null 2>&1 || true
done

echo "==> Starting the Java services"
"$pavex" compose up -d --no-build --wait "${java_services[@]}" >/dev/null

echo "==> Checking the result"
failures=0
expect() { # description, actual, expected
  if [[ "$2" == "$3" ]]; then echo "    ok    $1: $2"; else echo "    FAIL  $1: $2 (expected $3)"; failures=$((failures + 1)); fi
}
served="$(query pavex_partners "SELECT count(*) FROM partners.wards WHERE full_name <> 'Đặc khu Hoàng Sa'")"
provinces="$(query pavex_partners "SELECT count(*) FROM partners.provinces")"
expect "post offices (one per served ward)" "$(query pavex_network "SELECT count(*) FROM network.hubs WHERE type = 'LOCAL' AND status = 'ACTIVE'")" "$served"
expect "wards with more than one post office" "$(query pavex_network "SELECT count(*) FROM (SELECT ward_code FROM network.service_area_coverages WHERE is_active GROUP BY ward_code HAVING count(*) > 1) x")" 0
expect "provincial post offices (one per province)" "$(query pavex_network "SELECT count(DISTINCT province_code) FROM network.hubs WHERE type = 'PROVINCIAL'")" "$provinces"
expect "regional centres" "$(query pavex_network "SELECT count(*) FROM network.hubs WHERE type = 'REGIONAL_GATEWAY'")" 3
active_hubs="$(query pavex_network "SELECT count(*) FROM network.hubs WHERE status = 'ACTIVE'")"
expect "hubs with exactly one manager" "$(query pavex_operations "SELECT count(*) FROM (SELECT hm.hub_id FROM operations.hub_memberships hm JOIN operations.workforce_members m ON m.id = hm.workforce_member_id WHERE m.kind = 'HUB_MANAGER' AND hm.is_primary GROUP BY hm.hub_id HAVING count(*) = 1) x")" "$active_hubs"
expect "post offices without a lane to their provincial office" "$(query pavex_network "SELECT count(*) FROM network.hubs h WHERE h.type = 'LOCAL' AND h.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM network.hub_lanes l WHERE l.from_hub_id = h.id AND l.to_hub_id = h.parent_hub_id)")" 0
expect "post offices without a line-haul driver" "$(query pavex_operations "SELECT count(*) FROM (SELECT DISTINCT hm.hub_id FROM operations.hub_memberships hm JOIN operations.workforce_members m ON m.id = hm.workforce_member_id WHERE m.kind = 'HUB_MANAGER') managed WHERE NOT EXISTS (SELECT 1 FROM operations.hub_memberships d JOIN operations.workforce_members m ON m.id = d.workforce_member_id WHERE m.kind = 'LINE_HAUL_DRIVER' AND d.hub_id = managed.hub_id)")" 0
expect "@pavex.test accounts that can sign in" "$(query pavex_auth "SELECT count(*) FROM auth.user_account WHERE email LIKE '%@pavex.test'")" "$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["accounts"])' "$out_dir/summary.json")"

echo
python3 - "$out_dir/summary.json" <<'PY'
import json, sys
s = json.load(open(sys.argv[1]))
print(f"Seeded: {s['regionalCentres']} regional centres, {s['provincialOffices']} provincial post offices, "
      f"{s['postOffices']} post offices, {s['lanes']} lanes ({s['schedules']} daily departures), {s['routes']} routes, "
      f"{s['accounts']} accounts.")
for title, count in s["byTitle"].items():
    print(f"  {count:>6}  {title}")
PY
echo "Every @pavex.test account signs in with the password in scripts/seed-mini/README.md, e.g. admin@pavex.test,"
echo "vanhanh@pavex.test, truongbc.hn-badinh@pavex.test, buuta1.hn-badinh@pavex.test, taixe.kho-hn-t01@pavex.test."
echo "Staff list: ${out_dir#"$repo_dir"/}/staff.csv   Backup: ${backup_dir#"$repo_dir"/}"
echo "Shifts appear within a few minutes (Operations rosters every 2 minutes locally)."
((failures == 0)) || { echo "$failures check(s) failed." >&2; exit 1; }
