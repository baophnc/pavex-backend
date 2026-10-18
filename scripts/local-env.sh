#!/usr/bin/env bash
# Adds the local-only settings every service needs to .env at the repository root, so the services start
# the same way from ./scripts/dev-up.sh and from the IDE. application.yaml has no default for these on
# purpose: a real deployment that forgets one stops at startup. Keys already in .env are never changed,
# so edit .env to use other values. Never copy these values to a real deployment.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env_file="$(cd "$script_dir/.." && pwd)/.env"
touch "$env_file"
added=()

add() {
  key="$1"
  value="$2"
  grep -qE "^${key}=" "$env_file" && return 0
  if ((${#added[@]} == 0)); then
    # Keep the block on its own lines even if .env does not end with a newline.
    [[ -s "$env_file" && -n "$(tail -c 1 "$env_file")" ]] && printf '\n' >>"$env_file"
    printf '\n# Local-only values added by scripts/local-env.sh on %s. Never use them in a real deployment.\n' \
      "$(date +%Y-%m-%d)" >>"$env_file"
  fi
  printf '%s=%s\n' "$key" "$value" >>"$env_file"
  added+=("$key")
}

# Database, RabbitMQ and MinIO passwords of compose.yml.
for service in AUTH IDENTITY PARTNERS NETWORK PRICING SHIPMENT OPERATIONS BILLING FLEET NOTIFICATIONS; do
  add "${service}_DB_PASSWORD" pavex
done
add RABBITMQ_PASSWORD pavex
add MINIO_SECRET_KEY pavex-minio

# Keys that earlier versions kept in application.yaml; the same values keep existing local data readable.
add AUTH_KEY_HASH_SECRET cGF2ZXgtbG9jYWwtZGV2LWtleS0wMTIzNDU2Nzg5YWI=
add AUTH_OUTBOX_PAYLOAD_ENCRYPTION_KEY cGF2ZXgtbG9jYWwtb3V0Ym94LWtleS0wMTIzNDU2Nzg=
add BILLING_QR_SIGNING_KEY local-development-billing-qr-signing-key-0001

# First administrator admin@pavex.com, with a password generated for this machine. reset-password keeps the
# account on this password: change PAVEX_BOOTSTRAPADMIN_PASSWORD here and restart Auth to use another one.
add PAVEX_BOOTSTRAPADMIN_ENABLED true
add PAVEX_BOOTSTRAPADMIN_PASSWORD "$(head -c 18 /dev/urandom | base64 | tr -d '/+=\n')"
add PAVEX_BOOTSTRAPADMIN_RESET_PASSWORD true
# Accounts an administrator creates start with this password (the guides in docs/ use it).
add STAFF_TEMPORARY_PASSWORD 123456

# Every service runs on this machine: advertise loopback in Eureka, never a macOS .local hostname.
add EUREKA_INSTANCE_IP_ADDRESS 127.0.0.1
add EUREKA_INSTANCE_HOSTNAME 127.0.0.1
# API docs and Swagger UI (http://localhost:8080/swagger-ui.html) and the demo price list.
add SPRINGDOC_API_DOCS_ENABLED true
add SPRINGDOC_SWAGGER_UI_ENABLED true
add PRICING_DEMO_SEED_ENABLED true

if ((${#added[@]} > 0)); then
  echo "Added local-only settings to .env: ${added[*]}"
  if [[ " ${added[*]} " == *" PAVEX_BOOTSTRAPADMIN_PASSWORD "* ]]; then
    echo "Local admin admin@pavex.com signs in with PAVEX_BOOTSTRAPADMIN_PASSWORD from .env."
  fi
fi
