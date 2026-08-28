#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"
partners_private_key="$repo_dir/partners/.keys/private.pem"
partners_public_key="$repo_dir/partners/.keys/public.pem"
identity_url="${IDENTITY_SMOKE_URL:-http://localhost:8082}"
partners_url="${PARTNERS_SMOKE_URL:-http://localhost:8083}"

for tool in curl openssl; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "FAIL $tool is required" >&2
    exit 1
  }
done

pass() { printf 'PASS %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }

http_code() {
  curl --silent --output /dev/null --write-out '%{http_code}' "$@"
}

base64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

issue_partners_token() {
  local scope="$1"
  local now expires header payload unsigned signature
  now="$(date +%s)"
  expires="$((now + 60))"
  header='{"alg":"RS256","typ":"JWT"}'
  payload="{\"iss\":\"http://localhost:8083\",\"sub\":\"partners-service\",\"aud\":[\"identity-service\"],\"iat\":$now,\"exp\":$expires,\"jti\":\"smoke-$now-$RANDOM\",\"typ\":\"service\",\"scope\":\"$scope\"}"
  unsigned="$(printf '%s' "$header" | base64url).$(printf '%s' "$payload" | base64url)"
  signature="$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign "$partners_private_key" | base64url)"
  printf '%s.%s\n' "$unsigned" "$signature"
}

curl --fail --silent "$partners_url/actuator/health" >/dev/null \
  && pass "Partners health" \
  || fail "Partners health"

curl --fail --silent "$identity_url/actuator/health" >/dev/null \
  && pass "Identity health" \
  || fail "Identity health"

[[ -s "$partners_private_key" && -s "$partners_public_key" ]] \
  && pass "Partners service JWT key pair" \
  || fail "Partners service JWT key pair"

random_user_id="00000000-0000-0000-0000-000000000099"
eligibility_url="$identity_url/internal/v1/identities/$random_user_id/merchant-eligibility"

[[ "$(http_code "$eligibility_url")" == "401" ]] \
  && pass "Identity rejects missing Partners service JWT" \
  || fail "Identity rejects missing Partners service JWT"

wrong_scope_token="$(issue_partners_token 'identity.internal')"
[[ "$(http_code -H "Authorization: Bearer $wrong_scope_token" "$eligibility_url")" == "401" ]] \
  && pass "Identity rejects wrong Partners service scope" \
  || fail "Identity rejects wrong Partners service scope"

valid_token="$(issue_partners_token 'identity.merchant')"
valid_status="$(http_code -H "Authorization: Bearer $valid_token" "$eligibility_url")"
[[ "$valid_status" == "404" ]] \
  && pass "Identity accepts scoped Partners JWT" \
  || fail "Identity accepts scoped Partners JWT (expected 404 for smoke user, got $valid_status)"

# Auth's service JWT route must remain a separate boundary: a Partners token is
# not allowed to call the broader auth-context endpoint.
auth_context_url="$identity_url/internal/v1/identities/$random_user_id/auth-context"
[[ "$(http_code -H "Authorization: Bearer $valid_token" "$auth_context_url")" == "401" ]] \
  && pass "Partners JWT cannot access Auth internal Identity boundary" \
  || fail "Partners JWT cannot access Auth internal Identity boundary"

printf '\nPartners Patch 02 Identity integration smoke passed.\n'
