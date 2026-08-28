#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
local_dir="$repo_dir/.local"
auth_key_dir="$repo_dir/auth/.keys"
identity_key_dir="$repo_dir/identity/.keys"
env_file="$local_dir/dev.env"

command -v openssl >/dev/null 2>&1 || {
  echo "OpenSSL is required." >&2
  exit 1
}

mkdir -p "$local_dir/logs" "$local_dir/pids" "$auth_key_dir" "$identity_key_dir"

if [[ ! -s "$auth_key_dir/private.pem" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$auth_key_dir/private.pem" >/dev/null 2>&1
  chmod 600 "$auth_key_dir/private.pem"
fi

if [[ ! -s "$auth_key_dir/public.pem" ]]; then
  openssl pkey -in "$auth_key_dir/private.pem" -pubout -out "$auth_key_dir/public.pem" >/dev/null 2>&1
fi

cp "$auth_key_dir/public.pem" "$identity_key_dir/public.pem"

if [[ ! -s "$env_file" ]]; then
  outbox_key="$(openssl rand -base64 32 | tr -d '\n')"
  internal_token="$(openssl rand -hex 32)"
  {
    printf 'OUTBOX_PAYLOAD_ENCRYPTION_KEY=%s\n' "$outbox_key"
    printf 'IDENTITY_INTERNAL_TOKEN=%s\n' "$internal_token"
  } > "$env_file"
  chmod 600 "$env_file"
fi

echo "Local runtime material is ready under .local/ and */.keys/."
