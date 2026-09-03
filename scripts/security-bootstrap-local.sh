#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY_ROOT="$ROOT_DIR/.local/keys"
GITIGNORE="$ROOT_DIR/.gitignore"

ensure_gitignore_entry() {
  local entry="$1"
  touch "$GITIGNORE"
  if ! grep -Fqx -- "$entry" "$GITIGNORE"; then
    printf '%s\n' "$entry" >> "$GITIGNORE"
  fi
}

# Preserve the repository's existing ignore rules and append only missing security/build rules.
for entry in \
  ".local/" \
  "**/.keys/private.pem" \
  "**/.keys/public.pem" \
  "**/target/" \
  "__MACOSX/" \
  "*.tsbuildinfo" \
  ".DS_Store"; do
  ensure_gitignore_entry "$entry"
done

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required to generate local RSA keys." >&2
  exit 1
fi

umask 077
mkdir -p "$KEY_ROOT"
chmod 700 "$ROOT_DIR/.local" "$KEY_ROOT" 2>/dev/null || true

generate_pair() {
  local name="$1"
  local dir="$KEY_ROOT/$name"
  local private_key="$dir/private.pem"
  local public_key="$dir/public.pem"

  mkdir -p "$dir"
  chmod 700 "$dir" 2>/dev/null || true

  if [[ -e "$private_key" || -e "$public_key" ]]; then
    if [[ -s "$private_key" && -s "$public_key" ]]; then
      echo "[keep] $name local keypair already exists"
      return
    fi
    echo "Refusing to overwrite an incomplete keypair in $dir" >&2
    exit 1
  fi

  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$private_key" >/dev/null 2>&1
  openssl pkey -in "$private_key" -pubout -out "$public_key" >/dev/null 2>&1
  chmod 600 "$private_key" "$public_key"
  echo "[created] $name local keypair"
}

# Auth signs user access tokens. Every service issuer below gets its own keypair.
for issuer in auth identity partners pricing shipment operations; do
  generate_pair "$issuer"
done

legacy_keys=(
  auth/.keys/private.pem auth/.keys/public.pem
  identity/.keys/private.pem identity/.keys/public.pem
  partners/.keys/private.pem partners/.keys/public.pem
  pricing/.keys/private.pem pricing/.keys/public.pem
)

cd "$ROOT_DIR"

# Remove any checked-in local bootstrap password without copying its previous value
# into this distributed patch. The resulting config requires an environment secret.
auth_local_config="$ROOT_DIR/auth/src/main/resources/application-local.yaml"
if [[ -f "$auth_local_config" ]]; then
  tmp_config="$(mktemp)"
  awk '
    /^  bootstrap-admin:/ { in_bootstrap = 1 }
    in_bootstrap && /^    password:/ {
      print "    password: ${BOOTSTRAP_ADMIN_PASSWORD}"
      in_bootstrap = 0
      next
    }
    { print }
  ' "$auth_local_config" > "$tmp_config"
  mv "$tmp_config" "$auth_local_config"
fi

if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  # Stage removal without ever copying PEM contents into the distributed patch.
  git rm -f --ignore-unmatch -- "${legacy_keys[@]}" >/dev/null || true
else
  rm -f -- "${legacy_keys[@]}"
fi

echo
echo "Local JWT keys are ready under .local/keys/."
echo "Legacy checked-in key files were removed (and staged when running inside Git)."
echo "For the local Auth profile, set BOOTSTRAP_ADMIN_PASSWORD in your IntelliJ run environment."
echo "If any legacy private key was ever pushed to a remote repository, rotate it and purge it from Git history; deleting the current file is not sufficient."
