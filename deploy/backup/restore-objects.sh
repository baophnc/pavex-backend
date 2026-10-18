#!/bin/sh
# Puts the MinIO objects of a backup set back: every bucket of the set, or only the ones named. Objects written
# after the backup stay (they are only unreferenced files once the databases are restored too); deleted or
# damaged ones come back. Called by deploy/restore.sh.
#
#   pavex-restore-objects /backups/daily/2026-10-18_0230 [bucket ...]
set -eu
. /usr/local/lib/pavex-backup-common.sh
log_tag=restore
set_dir="${1:?Usage: pavex-restore-objects <backup set folder> [bucket ...]}"
shift
[ -f "$set_dir/objects.list" ] || { echo "$set_dir has no MinIO objects (no objects.list)" >&2; exit 1; }

# Refuse an incomplete set.
missing="$(while IFS= read -r path; do [ -f "$set_dir/objects/$path" ] || echo "$path"; done <"$set_dir/objects.list" \
  | head -n 5)"
[ -z "$missing" ] || { printf 'Missing from %s/objects:\n%s\n' "$set_dir" "$missing" >&2; exit 1; }

if [ $# -gt 0 ]; then
  buckets="$*"
else
  buckets="$(cd "$set_dir/objects" && ls -1)"
fi
mc_login
for bucket in $buckets; do
  [ -d "$set_dir/objects/$bucket" ] || { echo "No bucket $bucket in $set_dir" >&2; exit 1; }
  mc mb --ignore-existing "pavex/$bucket" >/dev/null
  mc mirror --overwrite --quiet "$set_dir/objects/$bucket" "pavex/$bucket" >/dev/null
  log "$bucket: $(grep -c "^$bucket/" "$set_dir/objects.list" || true) objects restored"
done
