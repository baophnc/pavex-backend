#!/bin/sh
# Dumps every pavex_* database (pg_dump custom format) plus the roles into /backups/daily/<date>/, copies every
# MinIO bucket next to them (pavex-backup-objects), keeps BACKUP_KEEP_DAILY daily sets and, from Sunday's set,
# BACKUP_KEEP_WEEKLY weekly ones; then copies them to S3-compatible storage when BACKUP_S3_BUCKET is set.
# Restore: deploy/restore.sh.
set -eu
. /usr/local/lib/pavex-backup-common.sh
PGPASSWORD="$(cat /run/secrets/POSTGRES_PASSWORD)"
export PGPASSWORD
root=/backups
stamp="$(date +%Y-%m-%d_%H%M)"
target="$root/daily/$stamp"
work="$root/.in-progress-$stamp"

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

# After the dumps: every photo a dumped row points to is already in MinIO. A failed copy must not cost the
# databases' backup: the set keeps the dumps, the job ends in error and BackupTooOld fires for MinIO.
objects_ok=true
if ! pavex-backup-objects "$work"; then
  objects_ok=false
  rm -rf "$work/objects" "$work/objects.list"
  log "MinIO copy FAILED: $target has the databases only"
fi
rm -rf "$target" # a second run within the same minute replaces the first
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
  offsite_remote
  # copy, never sync: an emptied local folder must not empty the offsite copy.
  rclone copy "$root" "$remote" --exclude '.in-progress-*/**' --exclude '/fetched/**' \
    --exclude '/{daily,weekly}/*/objects/**' --s3-no-check-bucket
  # Objects go once into one shared folder (keys never change, so the size is enough to skip what is there);
  # each set's objects.list says which of them belonged to it. pavex-fetch rebuilds a set from both.
  if $objects_ok; then
    rclone copy "$target/objects" "$remote/objects" --size-only --s3-no-check-bucket
  fi
  rclone delete "$remote" --min-age "${BACKUP_S3_KEEP:-35d}" --exclude '/objects/**' --rmdirs
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

# A failure keeps the last success time, so the alert counts from it; before any success it is 0 (alert at once).
objects_prom=/metrics/pavex_backup_objects.prom
if $objects_ok || [ ! -f "$objects_prom" ]; then
  if $objects_ok; then
    at="$(date +%s)" count="$(wc -l <"$target/objects.list")" bytes="$(du -sb "$target/objects" | cut -f1)"
  else
    at=0 count=0 bytes=0
  fi
  cat >"$objects_prom.tmp" <<PROM
# HELP pavex_backup_objects_last_success_timestamp_seconds End of the last successful copy of the MinIO buckets.
# TYPE pavex_backup_objects_last_success_timestamp_seconds gauge
pavex_backup_objects_last_success_timestamp_seconds $at
# HELP pavex_backup_objects_count Objects in the last copy of the MinIO buckets.
# TYPE pavex_backup_objects_count gauge
pavex_backup_objects_count $count
# HELP pavex_backup_objects_bytes Size of the last copy of the MinIO buckets.
# TYPE pavex_backup_objects_bytes gauge
pavex_backup_objects_bytes $bytes
PROM
  mv "$objects_prom.tmp" "$objects_prom"
fi
$objects_ok || exit 1
log "done"
