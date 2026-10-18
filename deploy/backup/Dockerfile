# Nightly pg_dump of every Pavex database plus a copy of every MinIO bucket (backup.sh), with the same PostgreSQL
# version as the server so the dumps restore with deploy/restore.sh, and the MinIO client of the server's image.
# rclone copies the sets to S3-compatible storage when configured.
FROM pgsty/minio:RELEASE.2026-08-04T00-00-00Z AS minio

FROM postgres:18.6-alpine
RUN apk add --no-cache rclone tzdata \
    && mkdir -p /root/.config/rclone && touch /root/.config/rclone/rclone.conf
# A static binary: runs on Alpine as is.
COPY --from=minio /usr/bin/mcli /usr/local/bin/mc
COPY common.sh /usr/local/lib/pavex-backup-common.sh
COPY entrypoint.sh /usr/local/bin/backup-entrypoint
COPY backup.sh /usr/local/bin/pavex-backup
COPY objects.sh /usr/local/bin/pavex-backup-objects
COPY restore-objects.sh /usr/local/bin/pavex-restore-objects
COPY fetch.sh /usr/local/bin/pavex-fetch
RUN chmod 755 /usr/local/bin/backup-entrypoint /usr/local/bin/pavex-*
ENTRYPOINT ["/usr/local/bin/backup-entrypoint"]
