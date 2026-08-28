#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
local_dir="$repo_dir/.local"

for tool in docker curl; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is required." >&2
    exit 1
  }
done

"$script_dir/dev-setup.sh"

set -a
# shellcheck disable=SC1091
source "$local_dir/dev.env"
set +a

export DB_URL="jdbc:postgresql://localhost:5433/pavex_auth"
export DB_USERNAME="pavex"
export DB_PASSWORD="pavex"
export IDENTITY_DB_URL="jdbc:postgresql://localhost:5434/pavex_identity"
export IDENTITY_DB_USERNAME="pavex"
export IDENTITY_DB_PASSWORD="pavex"
export JWT_ISSUER="http://localhost:8081"
export JWT_PRIVATE_KEY="file:$repo_dir/auth/.keys/private.pem"
export JWT_PUBLIC_KEY="file:$repo_dir/auth/.keys/public.pem"
export EUREKA_DEFAULT_ZONE="http://localhost:8761/eureka/"
export EUREKA_REGISTRY_FETCH_INTERVAL_SECONDS="5"

cleanup_started_apps() {
  for pid_file in "$local_dir"/pids/*.pid; do
    [[ -e "$pid_file" ]] || continue
    app_pid="$(<"$pid_file")"
    if kill -0 "$app_pid" 2>/dev/null; then
      kill "$app_pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  done
}

wait_for_health() {
  app_name="$1"
  health_url="$2"
  log_file="$3"
  app_pid="$4"

  for _ in $(seq 1 120); do
    if curl --fail --silent --max-time 2 "$health_url" >/dev/null 2>&1; then
      echo "$app_name is UP at $health_url"
      return 0
    fi
    if ! kill -0 "$app_pid" 2>/dev/null; then
      echo "$app_name stopped during startup. Last log lines:" >&2
      tail -n 40 "$log_file" >&2 || true
      return 1
    fi
    sleep 1
  done

  echo "$app_name did not become healthy. Last log lines:" >&2
  tail -n 40 "$log_file" >&2 || true
  return 1
}

start_app() {
  app_name="$1"
  jar_path="$2"
  health_url="$3"
  log_file="$local_dir/logs/$app_name.log"
  pid_file="$local_dir/pids/$app_name.pid"

  if curl --fail --silent --max-time 2 "$health_url" >/dev/null 2>&1; then
    echo "$app_name is already UP at $health_url"
    return 0
  fi

  if [[ -f "$pid_file" ]]; then
    stale_pid="$(<"$pid_file")"
    if kill -0 "$stale_pid" 2>/dev/null; then
      echo "$app_name is already starting with PID $stale_pid" >&2
      return 1
    fi
    rm -f "$pid_file"
  fi

  nohup java -jar "$repo_dir/$jar_path" </dev/null >"$log_file" 2>&1 &
  app_pid=$!
  printf '%s\n' "$app_pid" > "$pid_file"
  wait_for_health "$app_name" "$health_url" "$log_file" "$app_pid"
}

trap 'echo "Startup failed; stopping applications started by this script." >&2; cleanup_started_apps' ERR

cd "$repo_dir"
docker compose up -d --wait
# The previous branch may have generated classes under ignored target/
# directories. Clean first so stale Spring Boot main classes cannot leak into
# this branch's executable jars.
./mvnw -DskipTests clean install

start_app discovery-server platform/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar http://localhost:8761/actuator/health
start_app auth-service auth/target/auth-service-0.0.1-SNAPSHOT.jar http://localhost:8081/actuator/health
start_app identity-service identity/target/identity-service-0.0.1-SNAPSHOT.jar http://localhost:8082/actuator/health
start_app api-gateway platform/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar http://localhost:8080/actuator/health

trap - ERR
echo "Pavex local stack is running. Logs: $local_dir/logs"
