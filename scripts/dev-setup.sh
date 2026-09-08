#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
mkdir -p "$repo_dir/.local/logs" "$repo_dir/.local/pids"
exec "$script_dir/security-bootstrap-local.sh"
