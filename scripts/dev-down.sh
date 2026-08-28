#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
pid_dir="$repo_dir/.local/pids"

if [[ -d "$pid_dir" ]]; then
  for pid_file in "$pid_dir"/*.pid; do
    [[ -e "$pid_file" ]] || continue
    app_pid="$(<"$pid_file")"
    if kill -0 "$app_pid" 2>/dev/null; then
      kill "$app_pid"
    fi
    rm -f "$pid_file"
  done
fi

cd "$repo_dir"
docker compose down
echo "Pavex local stack stopped. Database volumes were preserved."
