#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
local_dir="$repo_dir/.local"
auth_key_dir="$repo_dir/auth/.keys"
identity_key_dir="$repo_dir/identity/.keys"
partners_key_dir="$repo_dir/partners/.keys"
env_file="$local_dir/dev.env"

command -v openssl >/dev/null 2>&1 || {
  echo "OpenSSL is required." >&2
  exit 1
}

mkdir -p "$local_dir/logs" "$local_dir/pids" "$auth_key_dir" "$identity_key_dir" "$partners_key_dir"

ensure_rsa_key_pair() {
  key_dir="$1"

  if [[ ! -s "$key_dir/private.pem" ]]; then
    openssl genpkey \
      -algorithm RSA \
      -pkeyopt rsa_keygen_bits:2048 \
      -out "$key_dir/private.pem" >/dev/null 2>&1
    chmod 600 "$key_dir/private.pem"
  fi

  # Always derive the public key from its private key so an older local setup
  # cannot leave a mismatched public key behind.
  openssl pkey \
    -in "$key_dir/private.pem" \
    -pubout \
    -out "$key_dir/public.pem" >/dev/null 2>&1
}

# Auth signs user JWTs and Auth -> Identity service JWTs.
ensure_rsa_key_pair "$auth_key_dir"

# Identity owns a different key pair for Identity -> Auth service JWTs.
ensure_rsa_key_pair "$identity_key_dir"

# Partners owns another key pair for narrowly scoped Partners -> Identity JWTs.
ensure_rsa_key_pair "$partners_key_dir"

touch "$env_file"
chmod 600 "$env_file"

if ! grep -q '^OUTBOX_PAYLOAD_ENCRYPTION_KEY=' "$env_file"; then
  outbox_key="$(openssl rand -base64 32 | tr -d '\n')"
  printf 'OUTBOX_PAYLOAD_ENCRYPTION_KEY=%s\n' "$outbox_key" >> "$env_file"
fi

if ! grep -q '^AUTH_SECURITY_KEY_HASH_SECRET=' "$env_file"; then
  security_hash_key="$(openssl rand -base64 32 | tr -d '\n')"
  printf 'AUTH_SECURITY_KEY_HASH_SECRET=%s\n' "$security_hash_key" >> "$env_file"
fi

echo "Local runtime material is ready under .local/ and */.keys/."
