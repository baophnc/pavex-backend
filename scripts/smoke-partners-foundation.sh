#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
cd "$repo_dir"

for tool in curl docker; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "$tool is required." >&2
    exit 1
  }
done

pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }

expect_http() {
  name="$1"
  expected="$2"
  url="$3"
  status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$url" || true)"
  [[ "$status" == "$expected" ]] || fail "$name (expected $expected, got $status)"
  pass "$name"
}

expect_http "Partners health" "200" "http://localhost:8083/actuator/health"
expect_http "Gateway Partners OpenAPI" "200" "http://localhost:8080/openapi/partners"

openapi="$(curl --fail --silent --max-time 5 http://localhost:8080/openapi/partners)"
grep -Fq 'Pavex Partners API' <<<"$openapi" || fail "Partners OpenAPI title"
pass "Partners OpenAPI title"

# A protected, not-yet-implemented Partners path should be rejected by
# Partners security before controller resolution. 401 also proves the Gateway
# route resolves to the service instead of falling through to an edge 404.
expect_http "Partners protected boundary" "401" "http://localhost:8080/api/v1/partners/me/addresses"

schema_exists="$(docker compose exec -T partners-db psql -U pavex -d pavex_partners -Atqc \
  "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name='partners') THEN 'yes' ELSE 'no' END")"
[[ "$schema_exists" == "yes" ]] || fail "Partners schema"
pass "Partners schema"

flyway_version="$(docker compose exec -T partners-db psql -U pavex -d pavex_partners -Atqc \
  "SELECT COALESCE(MAX(version), '') FROM flyway_schema_history WHERE success=true")"
[[ "$flyway_version" == "1" ]] || fail "Partners Flyway version (expected 1, got ${flyway_version:-empty})"
pass "Partners Flyway version"

wait_for_eureka_registration() {
  app="$1"
  for _ in $(seq 1 60); do
    status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 3 \
      -H 'Accept: application/json' "http://localhost:8761/eureka/apps/$app" || true)"
    if [[ "$status" == "200" ]]; then
      pass "Eureka registration $app"
      return 0
    fi
    sleep 1
  done
  fail "Eureka registration $app"
}

wait_for_eureka_registration PARTNERS-SERVICE

echo "Partners Patch 01 foundation smoke passed."
