#!/usr/bin/env bash
# Replaces the local dev data with the nationwide dataset (one post office per ward).
#
#   scripts/seed-nationwide/apply.sh [--day YYYY-MM-DD]
#
# 1. backs up every service database to .local/backups/<timestamp>/
# 2. stops the running services (databases keep running)
# 3. writes hubs, lanes, schedules, staff, shifts, shops and customers
# 4. clears shipments, quotes, cash and invoices that pointed at the old network
# 5. starts the stack again (scripts/dev-up.sh)
#
# Kept: admin@pavex.com and real accounts that are not seed accounts, the rate plans, and
# payout accounts. Every seeded account uses the password printed at the end.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
out_dir="$repo_dir/.local/seed/nationwide"
day="$(date +%F)"
restart=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --day) day="$2"; shift 2 ;;
    # Write into an already running stack (no build, no restart).
    --no-restart) restart=false; shift ;;
    *) echo "Unknown option $1" >&2; exit 2 ;;
  esac
done

psql_in() {
  local container="$1"
  docker exec -i "$container" sh -c 'psql -q -U "${POSTGRES_USER:-postgres}" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
}

echo "==> Generating dataset for $day"
python3 "$script_dir/generate.py" --out "$out_dir" --day "$day"

backup_dir="$repo_dir/.local/backups/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$backup_dir"
echo "==> Backing up databases to $backup_dir"
for db in auth identity partners network pricing operations shipment billing; do
  docker exec "pavex-backend-$db-db-1" sh -c 'pg_dump -U "${POSTGRES_USER:-postgres}" -Fc "$POSTGRES_DB"' > "$backup_dir/$db.dump"
done

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

echo "==> Writing network, accounts, workforce and shops"
for db in network identity auth operations partners; do
  psql_in "pavex-backend-$db-db-1" < "$out_dir/sql/$db.sql"
done

echo "==> Clearing shipments, quotes, cash and invoices of the old network"
psql_in pavex-backend-shipment-db-1 <<'SQL'
DO $$
DECLARE table_name text;
BEGIN
  FOR table_name IN SELECT tablename FROM pg_tables WHERE schemaname = 'shipments' LOOP
    EXECUTE format('TRUNCATE shipments.%I CASCADE', table_name);
  END LOOP;
END $$;
SQL
psql_in pavex-backend-pricing-db-1 <<'SQL'
TRUNCATE pricing.quote_requests CASCADE;
SQL
psql_in pavex-backend-billing-db-1 <<'SQL'
DO $$
DECLARE table_name text;
BEGIN
  FOR table_name IN SELECT tablename FROM pg_tables WHERE schemaname = 'billing' AND tablename <> 'merchant_payout_accounts' LOOP
    EXECUTE format('TRUNCATE billing.%I CASCADE', table_name);
  END LOOP;
END $$;
SQL
for queue in billing.shipment-events billing.shipment-events.dlq; do
  docker exec pavex-backend-rabbitmq-1 rabbitmqctl -q purge_queue "$queue" >/dev/null 2>&1 || true
done

if $restart; then
  echo "==> Starting services"
  "$repo_dir/scripts/dev-up.sh"
fi

echo
echo "Seed applied. Every seeded account signs in with the password: 1205@Phuc"
echo "Accounts and hubs: $out_dir/manifest.json (spreadsheet: scripts/seed-nationwide/export.py)"
echo "Backup to restore from: $backup_dir"
