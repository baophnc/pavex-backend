# Nightly pg_dump of every Pavex database (backup.sh), with the same PostgreSQL version as the server so the
# dumps restore with deploy/restore.sh. rclone copies them to S3-compatible storage when configured.
FROM postgres:18.6-alpine
RUN apk add --no-cache rclone tzdata \
    && mkdir -p /root/.config/rclone && touch /root/.config/rclone/rclone.conf
COPY entrypoint.sh /usr/local/bin/backup-entrypoint
COPY backup.sh /usr/local/bin/pavex-backup
RUN chmod 755 /usr/local/bin/backup-entrypoint /usr/local/bin/pavex-backup
ENTRYPOINT ["/usr/local/bin/backup-entrypoint"]
