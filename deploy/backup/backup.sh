#!/bin/sh
# Dumps every pavex_* database (pg_dump custom format) plus the roles into /backups/daily/<date>/,
# keeps BACKUP_KEEP_DAILY daily sets and, from Sunday's set, BACKUP_KEEP_WEEKLY weekly ones; then copies
# them to S3-compatible storage when BACKUP_S3_BUCKET is set. Restore: deploy/restore.sh.
set -eu
PGPASSWORD="$(cat /run/secrets/POSTGRES_PASSWORD)"
export PGPASSWORD
root=/backups
stamp="$(date +%Y-%m-%d_%H%M)"
target="$root/daily/$stamp"
work="$root/.in-progress-$stamp"
log() { echo "[backup $(date '+%F %T')] $*"; }

rm -rf "$work"
mkdir -p "$work" "$root/daily" "$root/weekly"
databases="$(psql -d postgres -tAc "SELECT datname FROM pg_database WHERE datname LIKE 'pavex\_%' ORDER BY 1")"
[ -n "$databases" ] || { log "no pavex_* database found"; exit 1; }
for db in $databases; do
  pg_dump -d "$db" -Fc -Z 6 -f "$work/$db.dump"
  log "$db: $(du -h "$work/$db.dump" | cut -f1)"
done
# Roles without their passwords: restore.sh recreates them from deploy/secrets when needed.
pg_dumpall --globals-only --no-role-passwords >"$work/globals.sql"
(cd "$work" && sha256sum ./*.dump globals.sql >SHA256SUMS)
mv "$work" "$target"
log "saved $target"

# Sunday's set is also the week's set (hard links: no extra space).
if [ "$(date +%u)" = 7 ]; then
  week="$root/weekly/$(date +%G-W%V)"
  rm -rf "$week"
  cp -al "$target" "$week"
  log "weekly copy $week"
fi

prune() { # keep the newest $2 folders of $1
  ls -1d "$1"/*/ 2>/dev/null | sort -r | tail -n +"$(($2 + 1))" | while read -r old; do
    rm -rf "$old"
    log "removed $old"
  done
}
prune "$root/daily" "${BACKUP_KEEP_DAILY:-7}"
prune "$root/weekly" "${BACKUP_KEEP_WEEKLY:-4}"

if [ -n "${BACKUP_S3_BUCKET:-}" ]; then
  export RCLONE_CONFIG_OFFSITE_TYPE=s3
  export RCLONE_CONFIG_OFFSITE_PROVIDER="${BACKUP_S3_PROVIDER:-Other}"
  export RCLONE_CONFIG_OFFSITE_ENDPOINT="${BACKUP_S3_ENDPOINT:-}"
  export RCLONE_CONFIG_OFFSITE_REGION="${BACKUP_S3_REGION:-}"
  export RCLONE_CONFIG_OFFSITE_ACCESS_KEY_ID="${BACKUP_S3_ACCESS_KEY:-}"
  export RCLONE_CONFIG_OFFSITE_SECRET_ACCESS_KEY="${BACKUP_S3_SECRET_KEY:-}"
  remote="offsite:${BACKUP_S3_BUCKET}/${BACKUP_S3_PREFIX:-pavex-staging}"
  # copy, never sync: an emptied local folder must not empty the offsite copy.
  rclone copy "$root" "$remote" --exclude '.in-progress-*/**' --s3-no-check-bucket
  rclone delete "$remote" --min-age "${BACKUP_S3_KEEP:-35d}" --rmdirs
  log "copied to $remote"
fi

# Read by node-exporter's textfile collector: the BackupTooOld alert and the Grafana panel.
size="$(du -sb "$target" | cut -f1)"
cat >/metrics/pavex_backup.prom.tmp <<PROM
# HELP pavex_backup_last_success_timestamp_seconds End of the last successful PostgreSQL backup.
# TYPE pavex_backup_last_success_timestamp_seconds gauge
pavex_backup_last_success_timestamp_seconds $(date +%s)
# HELP pavex_backup_last_size_bytes Size of the last backup set.
# TYPE pavex_backup_last_size_bytes gauge
pavex_backup_last_size_bytes $size
PROM
mv /metrics/pavex_backup.prom.tmp /metrics/pavex_backup.prom
log "done"
