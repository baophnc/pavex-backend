#!/bin/sh
# One-shot job of compose.local.yml: loads scripts/seed-mini (2 hubs, 12 test accounts, 2 shops) into a fresh
# local stack, after the services have created their tables. Does nothing when the seed accounts exist, so
# every `./pavex up` may run it; `./pavex reset` starts from empty databases and seeds again.
set -eu
PGPASSWORD="$(cat /run/secrets/POSTGRES_PASSWORD)"
export PGPASSWORD PGOPTIONS="-c client_min_messages=warning"
run() { db="$1"; shift; psql -v ON_ERROR_STOP=1 -q -d "$db" "$@" >/dev/null; }

# After `./pavex seed full` the nationwide dataset is in place and must not be replaced by seed-mini.
nationwide="$(psql -tA -d pavex_network -c "SELECT count(*) FROM network.hubs WHERE code = 'TT-MIENBAC'")"
if [ "$nationwide" != 0 ]; then
  echo "[seed] the nationwide dataset (./pavex seed full) is loaded: nothing to do."
  exit 0
fi

# The shops are loaded last: if they exist, every step ran. Each step can run again after a partial failure.
seeded="$(psql -tA -d pavex_partners -c "SELECT count(*) FROM partners.merchants WHERE merchant_code = 'PVM000001'")"
if [ "$seeded" != 0 ]; then
  echo "[seed] seed-mini accounts already exist: nothing to do."
  exit 0
fi

# The one password of every @pavex.test account lives in scripts/seed-mini/apply.sh.
password="$(sed -n 's/^SEED_PASSWORD="\(.*\)"$/\1/p' /seed-mini/apply.sh)"
[ -n "$password" ] || { echo "[seed] SEED_PASSWORD not found in scripts/seed-mini/apply.sh" >&2; exit 1; }

echo "[seed] network: 2 hubs, 2 line-haul lanes"
run pavex_network </seed-mini/network.sql
echo "[seed] identity: test accounts"
run pavex_identity </seed-mini/identity.sql

echo "[seed] auth: credentials of the same accounts"
psql -q -d postgres -c "CREATE EXTENSION IF NOT EXISTS pgcrypto"
hash="{bcrypt}$(echo "SELECT crypt(:'pw', gen_salt('bf', 10))" | psql -tA -d postgres -v pw="$password")"
psql -tA -F "$(printf '\t')" -d pavex_identity \
  -c "SELECT id, email FROM identity.user_account WHERE email LIKE '%@pavex.test'" >/tmp/seed-users.tsv
{
  echo "BEGIN;"
  echo "DELETE FROM auth.user_account WHERE email LIKE '%@pavex.test';"
  echo "CREATE TEMP TABLE seed_user (id uuid, email text) ON COMMIT DROP;"
  echo "COPY seed_user (id, email) FROM stdin;"
  cat /tmp/seed-users.tsv
  echo '\.'
  echo "INSERT INTO auth.user_account (id, email, password_hash, email_verified, failed_login_attempts, created_at,"
  echo "  updated_at, version, identity_provisioned, must_change_password)"
  echo "SELECT id, email, :'pwhash', TRUE, 0, NOW(), NOW(), 0, TRUE, FALSE FROM seed_user;"
  echo "COMMIT;"
} | psql -v ON_ERROR_STOP=1 -q -d pavex_auth -v pwhash="$hash"

echo "[seed] operations: staff of the 2 hubs"
run pavex_operations </seed-mini/operations.sql
echo "[seed] partners: 2 shops"
run pavex_partners </seed-mini/partners.sql

echo "[seed] done: every @pavex.test account signs in with the password in scripts/seed-mini/README.md"
