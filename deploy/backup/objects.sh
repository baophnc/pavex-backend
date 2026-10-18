#!/bin/sh
# Copies every MinIO bucket (delivery photos, support attachments, avatars, ...) into <set>/objects/<bucket>/<key>
# and lists the copy in <set>/objects.list, one "<bucket>/<key>" per line (rclone --files-from reads it).
# pavex-backup runs it for each set; when it fails, the set keeps the databases only.
#
# The services write each object once under a fresh UUID key and never change it (libs/platform-storage), so the
# set starts as hard links to the newest earlier set's objects and mc mirror only downloads what is new and drops
# what was deleted: every set is a full copy as of that night, but the disk holds each object once across all the
# kept sets (a full copy per set would need 11 times the bucket size for 7 daily + 4 weekly sets).
set -eu
. /usr/local/lib/pavex-backup-common.sh
log_tag=objects
set_dir="${1:?Usage: pavex-backup-objects <backup set folder>}"
target="$set_dir/objects"

mc_login
buckets="$(mc ls pavex | awk '{ sub("/$", "", $NF); print $NF }')"

rm -rf "$target"
previous="$(ls -1d /backups/daily/*/objects 2>/dev/null | sort -r | head -n 1)"
if [ -n "$previous" ]; then
  cp -al "$previous" "$target"
else
  mkdir -p "$target"
fi
# A bucket that no longer exists leaves the set.
for dir in "$target"/*/; do
  [ -d "$dir" ] || continue
  printf '%s\n' "$buckets" | grep -qxF "$(basename "$dir")" || rm -rf "$dir"
done
for bucket in $buckets; do
  mkdir -p "$target/$bucket"
  # mc writes each download to a temporary file and renames it, so a replaced file never changes the hard-linked
  # copy in an earlier set.
  mc mirror --overwrite --remove --quiet "pavex/$bucket" "$target/$bucket" >/dev/null
done

(cd "$target" && find . -type f | sed 's|^\./||' | sort) >"$set_dir/objects.list"
count="$(wc -l <"$set_dir/objects.list")"
log "$count objects in $(printf '%s' "$buckets" | wc -w) buckets, $(du -sh "$target" | cut -f1)"
