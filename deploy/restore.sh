#!/usr/bin/env bash
# Restores databases and MinIO objects from a backup set made by the backup container (deploy/backups/daily/<date>/,
# weekly/<week>/, or fetched/<date>/ from the offsite copy).
#
#   ./deploy/restore.sh deploy/backups/daily/2026-10-18_0230                  # every database and bucket of the set
#   ./deploy/restore.sh deploy/backups/daily/2026-10-18_0230 pavex_billing    # only these databases
#   ./deploy/restore.sh deploy/backups/daily/2026-10-18_0230 pavex-evidence   # only these buckets (photos)
#   ./deploy/restore.sh --yes ...                                             # no confirmation question
#
# Buckets first: every object of the set is put back into MinIO (objects written since stay). Then for each
# database: stops the service that owns it, drops and recreates the database (owner: the service's login),
# restores the dump, starts the service again. Everything written to those databases since the backup is lost.
# The stack must be running (at least postgres, minio and backup). Drill: docs section 9.7.6.
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
compose=(docker compose -f "$deploy_dir/compose.yml")

assume_yes=false
if [[ "${1:-}" == "--yes" ]]; then
  assume_yes=true
  shift
fi
set_dir="${1:?Usage: restore.sh [--yes] <backup set folder> [database | bucket ...]}"
shift
[[ -d "$set_dir" ]] || { echo "No such folder: $set_dir" >&2; exit 1; }
set_dir="$(cd "$set_dir" && pwd)"

service_of() {
  case "$1" in
    pavex_auth) echo auth-service ;;
    pavex_identity) echo identity-service ;;
    pavex_partners) echo partners-service ;;
    pavex_network) echo network-service ;;
    pavex_pricing) echo pricing-service ;;
    pavex_shipments) echo shipment-service ;;
    pavex_operations) echo operations-service ;;
    pavex_billing) echo billing-service ;;
    pavex_fleet) echo fleet-service ;;
    pavex_notifications) echo notifications-service ;;
    *) return 1 ;;
  esac
}

databases=()
buckets=()
if (($# > 0)); then
  for item in "$@"; do
    if [[ -f "$set_dir/$item.dump" ]]; then
      databases+=("$item")
    elif [[ -d "$set_dir/objects/$item" ]]; then
      buckets+=("$item")
    else
      echo "No dump or bucket named $item in $set_dir" >&2
      exit 1
    fi
  done
else
  for dump in "$set_dir"/*.dump; do
    databases+=("$(basename "$dump" .dump)")
  done
  if [[ -f "$set_dir/objects.list" ]]; then
    for dir in "$set_dir"/objects/*/; do
      [[ -d "$dir" ]] && buckets+=("$(basename "$dir")")
    done
  else
    echo "Note: this set has no MinIO objects (its copy failed that night); only the databases are restored."
  fi
fi
services=()
for db in ${databases[@]+"${databases[@]}"}; do
  service="$(service_of "$db")" || { echo "Unknown database: $db" >&2; exit 1; }
  services+=("$service")
done

# Refuse a damaged or incomplete set.
if [[ -f "$set_dir/SHA256SUMS" ]]; then
  if command -v sha256sum >/dev/null; then
    (cd "$set_dir" && sha256sum -c --quiet SHA256SUMS)
  else
    (cd "$set_dir" && shasum -a 256 -c --quiet SHA256SUMS)
  fi
fi

# The backup container sees the backup folder as /backups: the set is <kind>/<name> inside it.
container_set="/backups/$(basename "$(dirname "$set_dir")")/$(basename "$set_dir")"
if ((${#buckets[@]} > 0)); then
  "${compose[@]}" exec -T backup cat "$container_set/objects.list" </dev/null 2>/dev/null \
    | cmp -s - "$set_dir/objects.list" \
    || { echo "The backup container does not see $set_dir as $container_set (BACKUP_DIR in deploy/.env?)" >&2; exit 1; }
fi

echo "Restore from $set_dir"
if ((${#buckets[@]} > 0)); then
  echo "  MinIO buckets: ${buckets[*]} (objects written since the backup stay)"
fi
if ((${#databases[@]} > 0)); then
  echo "  Databases: ${databases[*]}"
  echo "  Stops: ${services[*]}. Data written to these databases after this backup is lost."
fi
if ! $assume_yes; then
  read -r -p "Type RESTORE to continue: " answer
  [[ "$answer" == "RESTORE" ]] || { echo "Cancelled."; exit 1; }
fi

if ((${#buckets[@]} > 0)); then
  "${compose[@]}" exec -T backup pavex-restore-objects "$container_set" "${buckets[@]}" </dev/null
fi
((${#databases[@]} > 0)) || { echo "Done."; exit 0; }

"${compose[@]}" stop "${services[@]}"

psql_admin() { "${compose[@]}" exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d postgres "$@"; }
for db in "${databases[@]}"; do
  echo "== $db"
  psql_admin -v db="$db" <<'SQL'
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :'db' AND pid <> pg_backend_pid();
SELECT format('DROP DATABASE IF EXISTS %I', :'db') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'db') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SQL
  # Objects end up owned by the service's login, as when Flyway created them.
  "${compose[@]}" exec -T postgres pg_restore -U postgres -d "$db" --no-owner --no-privileges \
    --role="$db" --single-transaction --exit-on-error <"$set_dir/$db.dump"
  echo "restored $db"
done

"${compose[@]}" up -d "${services[@]}"
echo "Done. Services are starting again: ${compose[*]} ps"
