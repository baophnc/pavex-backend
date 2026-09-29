#!/usr/bin/env bash
set -euo pipefail
# Compatibility entry point: keys are now local files, never Docker-managed.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-sync}" in
  init|sync)
    exec bash "$script_dir/security-bootstrap.sh"
    ;;
  status)
    for issuer in auth identity partners pricing shipment operations billing fleet; do
      key_dir="$HOME/.pavex/keys/$issuer"
      if [[ -s "$key_dir/private.pem" && -s "$key_dir/public.pem" ]]; then
        echo "[ready] $issuer -> $key_dir"
      else
        echo "[missing] $issuer -> $key_dir"
      fi
    done
    ;;
  *)
    echo "Usage: $0 [init|sync|status]" >&2
    exit 2
    ;;
esac
