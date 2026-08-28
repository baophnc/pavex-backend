#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

for tool in curl docker grep; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is required." >&2
    exit 1
  }
done

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
AUTH_URL="${AUTH_URL:-http://localhost:8081}"
IDENTITY_URL="${IDENTITY_URL:-http://localhost:8082}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"

pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; exit 1; }

expect_health() {
  local name="$1" url="$2"
  curl -fsS --max-time 5 "$url/actuator/health" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' \
    && pass "$name health" \
    || fail "$name health ($url)"
}

expect_status() {
  local expected="$1" label="$2"
  shift 2
  local code
  code="$(curl -sS --max-time 8 -o /dev/null -w '%{http_code}' "$@" || true)"
  [[ "$code" == "$expected" ]] \
    && pass "$label -> $expected" \
    || fail "$label expected $expected but got ${code:-curl-error}"
}

expect_health "Eureka" "$EUREKA_URL"
expect_health "Auth" "$AUTH_URL"
expect_health "Identity" "$IDENTITY_URL"
expect_health "Gateway" "$GATEWAY_URL"

docker compose exec -T gateway-redis redis-cli ping | grep -q '^PONG$' \
  && pass "Redis" || fail "Redis"

docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null \
  && pass "RabbitMQ" || fail "RabbitMQ"

registry="$(curl -fsS --max-time 5 -H 'Accept: application/json' "$EUREKA_URL/eureka/apps")"
for app in AUTH-SERVICE IDENTITY-SERVICE API-GATEWAY; do
  grep -q "\"name\"[[:space:]]*:[[:space:]]*\"$app\"" <<<"$registry" \
    && pass "Eureka registration $app" \
    || fail "Eureka registration $app"
done

# Public routes: these status codes prove the Gateway resolves each explicit
# route to a live service instead of returning 404/503.
expect_status 400 "Gateway auth route" \
  -X POST "$GATEWAY_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{}'
expect_status 401 "Gateway current-user route" \
  "$GATEWAY_URL/api/v1/auth/me"
expect_status 401 "Gateway users route" \
  "$GATEWAY_URL/api/v1/users"
expect_status 401 "Gateway roles route" \
  "$GATEWAY_URL/api/v1/roles"

# Internal endpoints must reject unauthenticated requests and the removed
# shared-header mechanism.
fake_user='00000000-0000-0000-0000-000000000000'
expect_status 401 "Identity internal API without service JWT" \
  "$IDENTITY_URL/internal/v1/identities/$fake_user/auth-context"
expect_status 401 "Identity rejects legacy internal header" \
  "$IDENTITY_URL/internal/v1/identities/$fake_user/auth-context" \
  -H 'X-Pavex-Internal-Token: legacy-token'
expect_status 401 "Auth internal API without service JWT" \
  -X POST "$AUTH_URL/internal/v1/credentials/$fake_user/revoke-sessions"

# Database shape and cutover invariants.
auth_version="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
[[ "$auth_version" == "18" ]] \
  && pass "Auth Flyway V18" \
  || fail "Auth Flyway expected V18, got ${auth_version:-none}"

identity_version="$(docker compose exec -T identity-db psql -U pavex -d pavex_identity -Atqc \
  "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
[[ "$identity_version" == "3" ]] \
  && pass "Identity Flyway V3" \
  || fail "Identity Flyway expected V3, got ${identity_version:-none}"

legacy_tables="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc \
  "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='auth' AND c.relname IN ('user_profile','user_role','role_permission','role','permission')")"
[[ "$legacy_tables" == "0" ]] \
  && pass "Legacy Auth identity/RBAC tables removed" \
  || fail "Legacy Auth identity/RBAC tables still present: $legacy_tables"

legacy_columns="$(docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc \
  "SELECT count(*) FROM information_schema.columns WHERE table_schema='auth' AND table_name='user_account' AND column_name IN ('status','profile_status')")"
[[ "$legacy_columns" == "0" ]] \
  && pass "Legacy Auth identity state columns removed" \
  || fail "Legacy Auth identity state columns still present: $legacy_columns"

auth_ids="$(mktemp)"
identity_ids="$(mktemp)"
role_ids="$(mktemp)"
trap 'rm -f "$auth_ids" "$identity_ids" "$role_ids"' EXIT

docker compose exec -T auth-db psql -U pavex -d pavex_auth -Atqc \
  'SELECT id FROM auth.user_account ORDER BY id' | sort > "$auth_ids"
docker compose exec -T identity-db psql -U pavex -d pavex_identity -Atqc \
  'SELECT id FROM identity.user_account ORDER BY id' | sort > "$identity_ids"
docker compose exec -T identity-db psql -U pavex -d pavex_identity -Atqc \
  'SELECT DISTINCT user_id FROM identity.user_role ORDER BY user_id' | sort > "$role_ids"

if [[ -n "$(comm -23 "$auth_ids" "$identity_ids")" ]]; then
  fail "Some Auth credentials have no Identity account"
fi
pass "Every Auth credential has an Identity account"

if [[ -n "$(comm -23 "$auth_ids" "$role_ids")" ]]; then
  fail "Some Auth credentials have no Identity role"
fi
pass "Every Auth credential has at least one Identity role"

if [[ -n "${SMOKE_EMAIL:-}" && -n "${SMOKE_PASSWORD:-}" ]]; then
  command -v python3 >/dev/null 2>&1 || fail "python3 is required for credential smoke test"
  login_body="$(curl -fsS --max-time 10 \
    -X POST "$GATEWAY_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    --data "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"$SMOKE_PASSWORD\"}")" \
    || fail "Credential login smoke test"
  access_token="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])' <<<"$login_body")"
  expect_status 200 "Authenticated /auth/me" \
    "$GATEWAY_URL/api/v1/auth/me" \
    -H "Authorization: Bearer $access_token"
  expect_status 200 "Authenticated session list" \
    "$GATEWAY_URL/api/v1/auth/sessions" \
    -H "Authorization: Bearer $access_token"
else
  echo "SKIP  credential login (set SMOKE_EMAIL and SMOKE_PASSWORD to enable)"
fi

echo
echo "Final Auth/Identity smoke checks passed."
