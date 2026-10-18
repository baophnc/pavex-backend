#!/usr/bin/env bash
# Creates every secret the staging stack needs in deploy/secrets/ (never committed, see .gitignore):
#
#   secrets/values/<NAME>          one random value per file: database, RabbitMQ, MinIO, Redis, Grafana
#                                  passwords, Auth/Billing keys, first admin and staff temporary passwords
#   secrets/keys/<issuer>/         the JWT key pairs (scripts/security-bootstrap.sh)
#   secrets/service-keys/<svc>/    what each service mounts read-only at /etc/pavex/keys: its own private key
#                                  and the public keys of the others
#   secrets/rabbitmq.conf          RabbitMQ user, password and vhost
#
# Safe to run again: existing values and keys are kept, only missing ones are created (so a rerun after an
# upgrade adds what a new version needs). To rotate one value, delete its file and rerun; database, RabbitMQ
# and MinIO passwords are only applied when their data volume is created (docs section 9.7).
#
# Files are readable by everyone but sit in a folder only you can open (0700): the containers run as
# other users (Spring services as uid 10001), and each container only mounts the files it needs.
set -euo pipefail

deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
secrets_dir="$deploy_dir/secrets"
command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required." >&2; exit 1; }

mkdir -p "$secrets_dir"
chmod 700 "$secrets_dir"
mkdir -p "$secrets_dir/values"
chmod 755 "$secrets_dir/values"

# Letters and digits only, so a value never needs quoting in a URL, a config file or a shell.
random_text() { openssl rand -base64 64 | tr -dc 'A-Za-z0-9' | head -c "$1"; }

created=()
add() {
  local name="$1" value="$2" file="$secrets_dir/values/$1"
  [[ -s "$file" ]] && return 0
  printf '%s' "$value" >"$file"
  chmod 644 "$file"
  created+=("$name")
}

add POSTGRES_PASSWORD "$(random_text 32)"
for service in AUTH IDENTITY PARTNERS NETWORK PRICING SHIPMENT OPERATIONS BILLING FLEET NOTIFICATIONS; do
  add "${service}_DB_PASSWORD" "$(random_text 32)"
done
add RABBITMQ_PASSWORD "$(random_text 32)"
add MINIO_SECRET_KEY "$(random_text 40)"
add REDIS_PASSWORD "$(random_text 32)"
add GRAFANA_ADMIN_PASSWORD "$(random_text 24)"
add AUTH_KEY_HASH_SECRET "$(openssl rand -base64 32)"
add AUTH_OUTBOX_PAYLOAD_ENCRYPTION_KEY "$(openssl rand -base64 32)"
add BILLING_QR_SIGNING_KEY "$(openssl rand -base64 48)"
# Accounts an administrator creates start with this password and must change it at the first sign-in.
add STAFF_TEMPORARY_PASSWORD "$(random_text 16)"
# The first administrator (PAVEX_BOOTSTRAPADMIN_EMAIL in .env); used only while PAVEX_BOOTSTRAPADMIN_ENABLED=true.
add PAVEX_BOOTSTRAPADMIN_PASSWORD "$(random_text 20)"

# RabbitMQ creates this user and vhost when its data volume is new.
rabbit_conf="$secrets_dir/rabbitmq.conf"
if [[ ! -s "$rabbit_conf" ]]; then
  {
    echo "default_user = pavex"
    echo "default_pass = $(cat "$secrets_dir/values/RABBITMQ_PASSWORD")"
    echo "default_vhost = pavex"
    echo "default_user_tags.administrator = true"
    echo "default_permissions.configure = .*"
    echo "default_permissions.read = .*"
    echo "default_permissions.write = .*"
  } >"$rabbit_conf"
  chmod 644 "$rabbit_conf"
  created+=(rabbitmq.conf)
fi

# JWT key pairs, generated the same way as for local development but in this folder.
bash "$deploy_dir/../scripts/security-bootstrap.sh" "$secrets_dir/keys" >/dev/null

# One read-only key folder per service: its own private key (Network signs nothing) and every public key.
issuers=(auth identity partners pricing shipment operations billing fleet notifications)
rm -rf "$secrets_dir/service-keys"
mkdir -p "$secrets_dir/service-keys"
for service in auth identity partners network pricing shipment operations billing fleet notifications; do
  for issuer in "${issuers[@]}"; do
    mkdir -p "$secrets_dir/service-keys/$service/$issuer"
    cp "$secrets_dir/keys/$issuer/public.pem" "$secrets_dir/service-keys/$service/$issuer/public.pem"
  done
  if [[ -f "$secrets_dir/keys/$service/private.pem" ]]; then
    cp "$secrets_dir/keys/$service/private.pem" "$secrets_dir/service-keys/$service/$service/private.pem"
  fi
done
find "$secrets_dir/service-keys" -type d -exec chmod 755 {} +
find "$secrets_dir/service-keys" -type f -exec chmod 644 {} +

if ((${#created[@]} > 0)); then
  echo "Created: ${created[*]}"
else
  echo "Every secret already existed; nothing changed."
fi
echo "Secrets are in $secrets_dir. Keep a copy outside this server, next to the backups (docs section 9.7)."
