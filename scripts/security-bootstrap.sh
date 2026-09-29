#!/usr/bin/env bash
set -euo pipefail

# Match pavex.keys-dir in every application.yaml, regardless of working directory.
# No Docker volumes, environment files or key rotation during normal startup.
key_root="$HOME/.pavex/keys"
command -v openssl >/dev/null 2>&1 || { echo "OpenSSL is required." >&2; exit 1; }
umask 077
mkdir -p "$key_root"
for issuer in auth identity partners pricing shipment operations billing fleet; do
  key_dir="$key_root/$issuer"
  mkdir -p "$key_dir"
  if [[ ! -s "$key_dir/private.pem" ]]; then
    if [[ -e "$key_dir/private.pem" || -e "$key_dir/public.pem" ]]; then
      echo "Incomplete keypair: $key_dir. Refusing to replace existing key material." >&2
      exit 1
    fi
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
      -out "$key_dir/private.pem" >/dev/null 2>&1
    echo "[created] $issuer development keypair"
  fi
  # Repair a stale public key from the existing private key; never rotate it.
  public_tmp="$(mktemp "$key_dir/public.XXXXXX")"
  if ! openssl pkey -in "$key_dir/private.pem" -pubout -out "$public_tmp"; then
    rm -f "$public_tmp"
    exit 1
  fi
  if [[ -f "$key_dir/public.pem" ]] && cmp -s "$public_tmp" "$key_dir/public.pem"; then
    rm -f "$public_tmp"
  else
    mv "$public_tmp" "$key_dir/public.pem"
  fi
  chmod 600 "$key_dir/private.pem"
  chmod 644 "$key_dir/public.pem"
  echo "[ready] $issuer -> $key_dir"
done
echo "JWT keys are ready. No Spring profile is required. Existing private keys were preserved."
