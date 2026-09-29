#!/usr/bin/env bash
# Replaces the local dev data with the nationwide dataset.
#
#   scripts/seed-nationwide/apply.sh [--staff-only] [--no-restart] [--day YYYY-MM-DD]
#
#   (default)      network (one post office per ward) + staff
#   --staff-only   keep the network, regenerate the staff only
#
# 1. backs up every service database to .local/backups/<timestamp>/
# 2. stops the running services (databases keep running)
# 3. writes the network (unless --staff-only) and the staff accounts and workforce
# 4. clears business data so testing starts clean: shops, shipments, quotes, cash, invoices,
#    line-haul trips; the MERCHANT role is removed from the accounts that are kept
# 5. starts the stack again (scripts/dev-up.sh); Operations then rosters a week of shifts
#
# Kept: admin@pavex.com, accounts that are not seed accounts (e.g. your own Gmail), the rate
# plans and the location catalogue. Every seeded account uses the password printed at the end.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
out_dir="$repo_dir/.local/seed/nationwide"
day="$(date +%F)"
restart=true
network=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --day) day="$2"; shift 2 ;;
    # Write into an already running stack (no build, no restart).
    --no-restart) restart=false; shift ;;
    --staff-only) network=false; shift ;;
    *) echo "Unknown option $1" >&2; exit 2 ;;
  esac
done

psql_in() {
  local container="$1"
  docker exec -i "$container" sh -c 'psql -q -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
}

truncate_schema() {
  local container="$1" schema="$2" keep="${3:-}"
  psql_in "$container" <<SQL
DO \$\$
DECLARE table_name text;
BEGIN
  FOR table_name IN SELECT tablename FROM pg_tables WHERE schemaname = '$schema' AND tablename <> '$keep' LOOP
    EXECUTE format('TRUNCATE $schema.%I CASCADE', table_name);
  END LOOP;
END \$\$;
SQL
}

backup_dir="$repo_dir/.local/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
echo "==> Backing up databases to $backup_dir"
for db in auth identity partners network pricing operations shipment billing fleet; do
  docker exec "pavex-backend-$db-db-1" sh -c 'pg_dump -U "${POSTGRES_USER:-postgres}" -Fc "$POSTGRES_DB"' > "$backup_dir/$db.dump"
done

if $network; then
  echo "==> Generating the network for $day"
  python3 "$script_dir/generate.py" --out "$out_dir" --day "$day"
fi

if $restart; then
  echo "==> Stopping services"
  pid_dir="$repo_dir/.local/pids"
  for pid_file in "$pid_dir"/*.pid; do
    [[ -e "$pid_file" ]] || continue
    app_pid="$(<"$pid_file")"
    if kill -0 "$app_pid" 2>/dev/null; then kill "$app_pid"; fi
    while kill -0 "$app_pid" 2>/dev/null; do sleep 1; done
    rm -f "$pid_file"
  done
fi

if $network; then
  echo "==> Writing the network"
  psql_in pavex-backend-network-db-1 < "$out_dir/sql/network.sql"
fi

# The staff generator reads the network that is now in the database.
echo "==> Generating staff"
python3 "$script_dir/staff.py" --out "$out_dir"
echo "==> Writing accounts and workforce"
for db in identity auth operations; do
  psql_in "pavex-backend-$db-db-1" < "$out_dir/sql/$db.sql"
done

echo "==> Clearing shops, shipments, quotes, cash, invoices and trips"
# Partners cannot read Identity, so the ids of the accounts that still exist are passed in.
docker exec pavex-backend-identity-db-1 sh -c \
  'psql -At -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -c "SELECT id FROM identity.user_account"' > "$out_dir/live-users.txt"
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
} | psql_in pavex-backend-partners-db-1
truncate_schema pavex-backend-shipment-db-1 shipments
truncate_schema pavex-backend-billing-db-1 billing
truncate_schema pavex-backend-fleet-db-1 fleet
psql_in pavex-backend-pricing-db-1 <<'SQL'
TRUNCATE pricing.quote_requests CASCADE;
SQL
psql_in pavex-backend-network-db-1 <<'SQL'
TRUNCATE network.lane_capacity_reservations CASCADE;
SQL
for queue in billing.shipment-events billing.shipment-events.dlq; do
  docker exec pavex-backend-rabbitmq-1 rabbitmqctl -q purge_queue "$queue" >/dev/null 2>&1 || true
done

if $restart; then
  echo "==> Starting services"
  "$repo_dir/scripts/dev-up.sh"
fi

echo
echo "Seed applied. Every seeded account signs in with the password: $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["password"])' "$out_dir/staff.json")"
echo "Staff list: $out_dir/staff.csv (sample accounts are marked in the last column)"
echo "Backup to restore from: $backup_dir"
