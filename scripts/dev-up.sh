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

# Runtime defaults live in each service's application.yaml. Real mail, payOS and Google settings
# go in .env at the repository root (see .env.example); the services inherit them from here.
# Do not source legacy .local/dev.env or override JWT key paths here.

# Reads KEY=VALUE lines without evaluating them, so values with spaces, $ or quotes stay intact.
# An empty value is skipped so the application.yaml default applies (Spring would otherwise use "").
# A variable already exported in the shell wins, e.g. BILLING_PAYMENT_PROVIDER=fake ./scripts/dev-up.sh
load_env_file() {
  local file="$1" line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]] || continue
    key="${BASH_REMATCH[1]}"
    value="${BASH_REMATCH[2]}"
    if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then
      value="${BASH_REMATCH[1]}"
    fi
    [[ -n "$value" ]] || continue
    [[ -n "${!key+x}" ]] || export "$key=$value"
  done < "$file"
}

if [[ -f "$repo_dir/.env" ]]; then
  load_env_file "$repo_dir/.env"
  echo "==> Loaded settings from .env"
fi

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

preflight_auth_identity_cutover() {
  local user_table_exists legacy_rbac_exists account_count marker_table_exists marker_complete

  user_table_exists="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc     "SELECT CASE WHEN to_regclass('auth.user_account') IS NOT NULL THEN 'yes' ELSE 'no' END")"

  # A brand-new database has not run Auth Flyway yet. V15 is safe there because
  # there are no legacy users to migrate.
  if [[ "$user_table_exists" != "yes" ]]; then
    return 0
  fi

  legacy_rbac_exists="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc     "SELECT CASE WHEN to_regclass('auth.user_role') IS NOT NULL THEN 'yes' ELSE 'no' END")"

  # V15 has already finalized this database.
  if [[ "$legacy_rbac_exists" != "yes" ]]; then
    return 0
  fi

  account_count="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc     'SELECT COUNT(*) FROM auth.user_account')"

  if [[ "$account_count" == "0" ]]; then
    return 0
  fi

  marker_table_exists="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc     "SELECT CASE WHEN to_regclass('auth.identity_cutover_marker') IS NOT NULL THEN 'yes' ELSE 'no' END")"

  marker_complete="no"
  if [[ "$marker_table_exists" == "yes" ]]; then
    marker_complete="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc       "SELECT CASE WHEN EXISTS (SELECT 1 FROM auth.identity_cutover_marker WHERE id=1 AND completed_at IS NOT NULL) THEN 'yes' ELSE 'no' END")"
  fi

  if [[ "$marker_complete" != "yes" ]]; then
    cat >&2 <<'EOF'
Existing Auth users still need the one-time Auth -> Identity cutover.
Identity has already been started first so its V1-V3 schema is ready.

Run:
  ./scripts/cutover-auth-identity.sh --apply

Then rerun:
  ./scripts/dev-up.sh
EOF
    return 1
  fi
}

start_app discovery-server platform/discovery-server/target/discovery-server-0.0.1-SNAPSHOT.jar http://localhost:8761/actuator/health

# Start Identity before Auth. This guarantees Identity Flyway V1-V3 is ready
# before a legacy Auth database is allowed to cross the destructive V15 cutover.
start_app identity-service identity/target/identity-service-0.0.1-SNAPSHOT.jar http://localhost:8082/actuator/health
preflight_auth_identity_cutover

start_app auth-service auth/target/auth-service-0.0.1-SNAPSHOT.jar http://localhost:8081/actuator/health
start_app partners-service partners/target/partners-service-0.0.1-SNAPSHOT.jar http://localhost:8083/actuator/health
start_app network-service network/target/network-service-0.0.1-SNAPSHOT.jar http://localhost:8085/actuator/health
start_app pricing-service pricing/target/pricing-service-0.0.1-SNAPSHOT.jar http://localhost:8086/actuator/health
start_app operations-service operations/target/operations-service-0.0.1-SNAPSHOT.jar http://localhost:8087/actuator/health
start_app shipment-service shipment/target/shipment-service-0.0.1-SNAPSHOT.jar http://localhost:8084/actuator/health
start_app billing-service billing/target/billing-service-0.0.1-SNAPSHOT.jar http://localhost:8088/actuator/health
start_app fleet-service fleet/target/fleet-service-0.0.1-SNAPSHOT.jar http://localhost:8089/actuator/health
start_app api-gateway platform/api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar http://localhost:8080/actuator/health

trap - ERR
echo "Pavex local stack is running. Logs: $local_dir/logs"
