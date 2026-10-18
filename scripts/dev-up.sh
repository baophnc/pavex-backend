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

mkdir -p "$local_dir/logs" "$local_dir/pids"
# Creates the JWT key pair of any service that has none yet (in ~/.pavex/keys); existing keys are kept.
bash "$script_dir/security-bootstrap.sh"

# Settings such as Google, real email or payOS live in .env at the repository root (see .env.example).
# Every service reads that file itself, so the same settings apply when a service is started from the IDE.
# Passwords, keys and the local admin have no default in application.yaml: add the local values to .env.
bash "$script_dir/local-env.sh"

started_pid_files=()

cleanup_started_apps() {
  for pid_file in "${started_pid_files[@]}"; do
    [[ -n "$pid_file" && -e "$pid_file" ]] || continue
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
      echo "$app_name is already starting with PID $stale_pid; waiting for health"
      wait_for_health "$app_name" "$health_url" "$log_file" "$stale_pid"
      return 0
    fi
    rm -f "$pid_file"
  fi

  nohup java -jar "$repo_dir/$jar_path" </dev/null >"$log_file" 2>&1 &
  app_pid=$!
  printf '%s\n' "$app_pid" > "$pid_file"
  started_pid_files+=("$pid_file")
  wait_for_health "$app_name" "$health_url" "$log_file" "$app_pid"
}

trap 'echo "Startup failed; stopping applications started by this script." >&2; cleanup_started_apps' ERR

cd "$repo_dir"
docker compose up -d --wait
# The previous branch may have generated classes under ignored target/
# directories. Clean first so stale Spring Boot main classes cannot leak into
# this branch's executable jars.
./mvnw -DskipTests clean install

# Wait on readiness, not the full health: with real SMTP in .env the mail check of auth and partners
# can take longer than the probe timeout although the service is up.
start_app discovery-server platform/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar http://localhost:8761/actuator/health/readiness

# Identity before Auth: Auth reads accounts and roles from Identity at sign-in.
start_app identity-service identity/target/identity-service-0.0.1-SNAPSHOT.jar http://localhost:8082/actuator/health/readiness

start_app auth-service auth/target/auth-service-0.0.1-SNAPSHOT.jar http://localhost:8081/actuator/health/readiness
start_app partners-service partners/target/partners-service-0.0.1-SNAPSHOT.jar http://localhost:8083/actuator/health/readiness
start_app network-service network/target/network-service-0.0.1-SNAPSHOT.jar http://localhost:8085/actuator/health/readiness
start_app pricing-service pricing/target/pricing-service-0.0.1-SNAPSHOT.jar http://localhost:8086/actuator/health/readiness
start_app operations-service operations/target/operations-service-0.0.1-SNAPSHOT.jar http://localhost:8087/actuator/health/readiness
start_app shipment-service shipment/target/shipment-service-0.0.1-SNAPSHOT.jar http://localhost:8084/actuator/health/readiness
start_app billing-service billing/target/billing-service-0.0.1-SNAPSHOT.jar http://localhost:8088/actuator/health/readiness
start_app fleet-service fleet/target/fleet-service-0.0.1-SNAPSHOT.jar http://localhost:8089/actuator/health/readiness
start_app notifications-service notifications/target/notifications-service-0.0.1-SNAPSHOT.jar http://localhost:8090/actuator/health/readiness
start_app api-gateway platform/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar http://localhost:8080/actuator/health/readiness

trap - ERR
echo "Pavex local stack is running. Logs: $local_dir/logs"
