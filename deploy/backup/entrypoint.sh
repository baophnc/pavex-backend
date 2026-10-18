#!/bin/sh
# Runs pavex-backup (databases and MinIO objects) on BACKUP_SCHEDULE (cron syntax, time zone TZ) with busybox crond.
# Run one now: docker compose -f deploy/compose.yml exec backup pavex-backup
set -eu
# crond starts jobs with an empty environment: keep this container's settings for the job.
export -p | grep -E "^export (PG|BACKUP_|MINIO_|TZ)" >/etc/pavex-backup.env
echo "${BACKUP_SCHEDULE:-30 2 * * *} . /etc/pavex-backup.env; /usr/local/bin/pavex-backup >/proc/1/fd/1 2>/proc/1/fd/2" \
  >/etc/crontabs/root
echo "Backups scheduled at '${BACKUP_SCHEDULE:-30 2 * * *}' (${TZ:-UTC}) into /backups."
exec crond -f -l 8
