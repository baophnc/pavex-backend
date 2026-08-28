#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/.." && pwd)"

if [[ "${1:-}" != "--apply" ]]; then
  cat <<'EOF'
Usage:
  ./scripts/cutover-auth-identity.sh --apply

This is a one-time, idempotent cutover for an existing pre-final-split database.
It copies users/profiles and any still-missing role assignments from auth-db to
identity-db, verifies that every Auth credential has an Identity record, and
then writes the cutover marker required by Auth Flyway V15.

Stop the Auth and Identity application processes before running it. Keep the
PostgreSQL containers running.
EOF
  exit 2
fi

cd "$repo_dir"

command -v docker >/dev/null 2>&1 || {
  echo "Docker is required." >&2
  exit 1
}

compose=(docker compose -f "$repo_dir/compose.yml")

auth_psql() {
  "${compose[@]}" exec -T auth-db \
    psql -X -v ON_ERROR_STOP=1 -U pavex -d pavex_auth "$@"
}

identity_psql() {
  "${compose[@]}" exec -T identity-db \
    psql -X -v ON_ERROR_STOP=1 -U pavex -d pavex_identity "$@"
}

require_running() {
  local service="$1"
  if ! "${compose[@]}" ps --status running --services | grep -Fxq "$service"; then
    echo "Compose service '$service' is not running." >&2
    echo "Start the databases first:" >&2
    echo "  docker compose up -d auth-db identity-db" >&2
    exit 1
  fi
}

require_running auth-db
require_running identity-db

identity_ready="$(identity_psql -Atqc "
SELECT CASE
  WHEN to_regclass('identity.user_account') IS NOT NULL
   AND to_regclass('identity.user_profile') IS NOT NULL
   AND to_regclass('identity.role') IS NOT NULL
   AND to_regclass('identity.user_role') IS NOT NULL
  THEN 'yes'
  ELSE 'no'
END;")"

if [[ "$identity_ready" != "yes" ]]; then
  echo "Identity schema is not ready. Start identity-service once so Flyway V1-V3 can run, then stop the app and retry." >&2
  exit 1
fi

marker_exists="$(auth_psql -Atqc "SELECT CASE WHEN to_regclass('auth.identity_cutover_marker') IS NOT NULL THEN 'yes' ELSE 'no' END;")"
legacy_rbac_exists="$(auth_psql -Atqc "SELECT CASE WHEN to_regclass('auth.user_role') IS NOT NULL THEN 'yes' ELSE 'no' END;")"

# A completed database may already have applied V15 and dropped the legacy
# tables. In that case, do a verification-only pass and return successfully.
if [[ "$legacy_rbac_exists" != "yes" ]]; then
  if [[ "$marker_exists" != "yes" ]]; then
    echo "Legacy Auth RBAC tables are missing but the cutover marker is also missing. Refusing to guess database state." >&2
    exit 1
  fi

  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pavex-cutover.XXXXXX")"
  trap 'rm -rf "$work_dir"' EXIT
  auth_ids="$work_dir/auth-user-ids.txt"
  identity_ids="$work_dir/identity-user-ids.txt"
  identity_role_user_ids="$work_dir/identity-role-user-ids.txt"

  auth_psql -Atqc "SELECT id FROM auth.user_account ORDER BY id" | sort > "$auth_ids"
  identity_psql -Atqc "SELECT id FROM identity.user_account ORDER BY id" | sort > "$identity_ids"
  identity_psql -Atqc "SELECT DISTINCT user_id FROM identity.user_role ORDER BY user_id" | sort > "$identity_role_user_ids"

  missing_users="$(comm -23 "$auth_ids" "$identity_ids")"
  missing_roles="$(comm -23 "$auth_ids" "$identity_role_user_ids")"

  if [[ -n "$missing_users" || -n "$missing_roles" ]]; then
    echo "Existing cutover verification failed." >&2
    [[ -n "$missing_users" ]] && { echo "Missing Identity users:" >&2; printf '%s\n' "$missing_users" >&2; }
    [[ -n "$missing_roles" ]] && { echo "Users without Identity roles:" >&2; printf '%s\n' "$missing_roles" >&2; }
    exit 1
  fi

  echo "Auth -> Identity cutover is already finalized and verified."
  exit 0
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pavex-cutover.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT

sql_file="$work_dir/identity-cutover.sql"
auth_ids="$work_dir/auth-user-ids.txt"
identity_ids="$work_dir/identity-user-ids.txt"
auth_profile_ids="$work_dir/auth-profile-ids.txt"
identity_profile_ids="$work_dir/identity-profile-ids.txt"
identity_role_user_ids="$work_dir/identity-role-user-ids.txt"

cat > "$sql_file" <<'SQL'
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE cutover_new_user (
    id UUID PRIMARY KEY
) ON COMMIT DROP;

CREATE TEMP TABLE cutover_needs_roles (
    id UUID PRIMARY KEY
) ON COMMIT DROP;
SQL

# Insert only users that Identity does not already own. Existing Identity state
# wins; this prevents the one-time legacy copy from undoing status/RBAC changes
# already made after the split.
auth_psql -At <<'SQL' >> "$sql_file"
SELECT format(
$statement$
WITH inserted AS (
    INSERT INTO identity.user_account (
        id,
        email,
        status,
        profile_status,
        email_verified,
        authorization_version,
        created_at,
        updated_at,
        version
    )
    VALUES (
        %L::uuid,
        %L,
        %L,
        %L,
        %L::boolean,
        0,
        %L::timestamptz,
        %L::timestamptz,
        0
    )
    ON CONFLICT (id) DO NOTHING
    RETURNING id
)
INSERT INTO cutover_new_user (id)
SELECT id FROM inserted
ON CONFLICT DO NOTHING;
$statement$,
    id,
    email,
    status,
    profile_status,
    email_verified,
    created_at,
    updated_at
)
FROM auth.user_account
ORDER BY created_at, id;
SQL

# Fill a missing Identity profile, but never overwrite a profile Identity
# already owns.
auth_psql -At <<'SQL' >> "$sql_file"
SELECT format(
$statement$
INSERT INTO identity.user_profile (
    user_id,
    first_name,
    last_name,
    display_name,
    gender,
    date_of_birth,
    avatar_url,
    created_at,
    updated_at
)
VALUES (
    %L::uuid,
    %L,
    %L,
    %L,
    %L,
    %L::date,
    %L,
    %L::timestamptz,
    %L::timestamptz
)
ON CONFLICT (user_id) DO NOTHING;
$statement$,
    user_id,
    first_name,
    last_name,
    display_name,
    gender,
    date_of_birth,
    avatar_url,
    created_at,
    updated_at
)
FROM auth.user_profile
ORDER BY user_id;
SQL

# Existing Identity RBAC remains authoritative. Only users with no Identity
# role at all are eligible to receive their legacy Auth role set.
auth_psql -At <<'SQL' >> "$sql_file"
SELECT format(
$statement$
INSERT INTO cutover_needs_roles (id)
SELECT %L::uuid
WHERE NOT EXISTS (
    SELECT 1
    FROM identity.user_role
    WHERE user_id = %L::uuid
)
ON CONFLICT DO NOTHING;
$statement$,
    id,
    id
)
FROM auth.user_account
ORDER BY id;
SQL

auth_psql -At <<'SQL' >> "$sql_file"
SELECT format(
$statement$
INSERT INTO identity.user_role (
    user_id,
    role_id,
    assigned_at,
    assigned_by
)
SELECT
    %L::uuid,
    r.id,
    %L::timestamptz,
    %L::uuid
FROM identity.role r
WHERE r.code = %L
  AND EXISTS (
      SELECT 1
      FROM cutover_needs_roles
      WHERE id = %L::uuid
  )
ON CONFLICT DO NOTHING;
$statement$,
    ur.user_id,
    ur.assigned_at,
    ur.assigned_by,
    r.code,
    ur.user_id
)
FROM auth.user_role ur
JOIN auth.role r ON r.id = ur.role_id
ORDER BY ur.user_id, r.code;
SQL

cat >> "$sql_file" <<'SQL'
-- If a legacy credential had no role assignment, give it the platform default
-- instead of allowing an empty authorization context.
INSERT INTO identity.user_role (
    user_id,
    role_id,
    assigned_at,
    assigned_by
)
SELECT
    candidates.id,
    customer.id,
    CURRENT_TIMESTAMP,
    NULL
FROM cutover_needs_roles candidates
JOIN identity.role customer ON customer.code = 'CUSTOMER'
WHERE NOT EXISTS (
    SELECT 1
    FROM identity.user_role existing
    WHERE existing.user_id = candidates.id
)
ON CONFLICT DO NOTHING;

UPDATE identity.user_account account
SET authorization_version = account.authorization_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE account.id IN (
    SELECT id FROM cutover_needs_roles
);

COMMIT;
SQL

echo "Applying Auth -> Identity data cutover..."
identity_psql < "$sql_file"

# Verify the invariant required after deleting Auth-side RBAC code: every Auth
# credential must have an Identity account and at least one Identity role.
auth_psql -Atqc "SELECT id FROM auth.user_account ORDER BY id" | sort > "$auth_ids"
identity_psql -Atqc "SELECT id FROM identity.user_account ORDER BY id" | sort > "$identity_ids"
auth_psql -Atqc "SELECT user_id FROM auth.user_profile ORDER BY user_id" | sort > "$auth_profile_ids"
identity_psql -Atqc "SELECT user_id FROM identity.user_profile ORDER BY user_id" | sort > "$identity_profile_ids"
identity_psql -Atqc "SELECT DISTINCT user_id FROM identity.user_role ORDER BY user_id" | sort > "$identity_role_user_ids"

missing_users="$(comm -23 "$auth_ids" "$identity_ids")"
missing_profiles="$(comm -23 "$auth_profile_ids" "$identity_profile_ids")"
missing_roles="$(comm -23 "$auth_ids" "$identity_role_user_ids")"

if [[ -n "$missing_users" ]]; then
  echo "Cutover verification failed: these Auth users are missing from Identity:" >&2
  printf '%s\n' "$missing_users" >&2
  exit 1
fi

if [[ -n "$missing_profiles" ]]; then
  echo "Cutover verification failed: these Auth profiles are missing from Identity:" >&2
  printf '%s\n' "$missing_profiles" >&2
  exit 1
fi

if [[ -n "$missing_roles" ]]; then
  echo "Cutover verification failed: these Auth users have no Identity role:" >&2
  printf '%s\n' "$missing_roles" >&2
  exit 1
fi

auth_user_count="$(wc -l < "$auth_ids" | tr -d ' ')"
identity_user_count="$(wc -l < "$identity_ids" | tr -d ' ')"

# V15 checks this marker before removing the obsolete Auth profile/RBAC tables.
auth_psql <<SQL
CREATE TABLE IF NOT EXISTS auth.identity_cutover_marker (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    completed_at TIMESTAMPTZ NOT NULL,
    auth_user_count BIGINT NOT NULL,
    identity_user_count BIGINT NOT NULL
);

INSERT INTO auth.identity_cutover_marker (
    id,
    completed_at,
    auth_user_count,
    identity_user_count
)
VALUES (
    1,
    CURRENT_TIMESTAMP,
    ${auth_user_count},
    ${identity_user_count}
)
ON CONFLICT (id)
DO UPDATE SET
    completed_at = EXCLUDED.completed_at,
    auth_user_count = EXCLUDED.auth_user_count,
    identity_user_count = EXCLUDED.identity_user_count;
SQL

echo
printf 'Cutover verified. Auth users: %s; Identity users: %s\n' \
  "$auth_user_count" "$identity_user_count"
echo "The Auth service may now start and apply Flyway V15."
