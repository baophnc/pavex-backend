#!/bin/sh
# Downloads a backup set from the offsite copy (BACKUP_S3_*) into /backups/fetched/<name>, MinIO objects included,
# ready for deploy/restore.sh deploy/backups/fetched/<name>. Without an argument: lists the offsite sets.
#
#   docker compose -f deploy/compose.yml exec backup pavex-fetch
#   docker compose -f deploy/compose.yml exec backup pavex-fetch daily/2026-10-18_0230
set -eu
. /usr/local/lib/pavex-backup-common.sh
log_tag=fetch
offsite_remote
if [ $# = 0 ]; then
  for kind in daily weekly; do
    rclone lsf --dirs-only "$remote/$kind" 2>/dev/null | sed "s|^|$kind/|; s|/$||"
  done
  exit 0
fi
name="${1%/}"
case "$name" in daily/?* | weekly/?*) ;; *) echo "Usage: pavex-fetch [daily/<date> | weekly/<week>]" >&2; exit 1 ;; esac
dest="/backups/fetched/$(basename "$name")"

rclone copy "$remote/$name" "$dest" --exclude '/objects/**'
[ -f "$dest/SHA256SUMS" ] || { echo "No backup set at $remote/$name" >&2; exit 1; }
if [ -f "$dest/objects.list" ]; then
  rclone copy "$remote/objects" "$dest/objects" --files-from "$dest/objects.list" --size-only
fi
log "fetched $remote/$name into $dest (deploy/backups/fetched/$(basename "$name") on the server)"
