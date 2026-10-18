# Sourced by pavex-backup, pavex-backup-objects, pavex-restore-objects and pavex-fetch.

log() { echo "[${log_tag:-backup} $(date '+%F %T')] $*"; }

# The MinIO client, logged in to the stack's MinIO as alias "pavex". Keys go through stdin, not the command line;
# the config stays inside the container, never in /backups.
mc_login() {
  export MC_CONFIG_DIR=/tmp/pavex-mc
  printf '%s\n%s\n' "${MINIO_ACCESS_KEY:-pavex}" "$(cat /run/secrets/MINIO_SECRET_KEY)" \
    | mc alias set pavex "${MINIO_ENDPOINT:-http://minio:9000}" >/dev/null
}

# rclone remote "offsite:" from BACKUP_S3_*; sets $remote to the backup root on it.
offsite_remote() {
  [ -n "${BACKUP_S3_BUCKET:-}" ] || { echo "BACKUP_S3_BUCKET is not set" >&2; return 1; }
  export RCLONE_CONFIG_OFFSITE_TYPE=s3
  export RCLONE_CONFIG_OFFSITE_PROVIDER="${BACKUP_S3_PROVIDER:-Other}"
  export RCLONE_CONFIG_OFFSITE_ENDPOINT="${BACKUP_S3_ENDPOINT:-}"
  export RCLONE_CONFIG_OFFSITE_REGION="${BACKUP_S3_REGION:-}"
  export RCLONE_CONFIG_OFFSITE_ACCESS_KEY_ID="${BACKUP_S3_ACCESS_KEY:-}"
  export RCLONE_CONFIG_OFFSITE_SECRET_ACCESS_KEY="${BACKUP_S3_SECRET_KEY:-}"
  remote="offsite:${BACKUP_S3_BUCKET}/${BACKUP_S3_PREFIX:-pavex-staging}"
}
