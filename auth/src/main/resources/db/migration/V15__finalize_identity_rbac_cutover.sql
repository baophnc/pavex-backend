-- Finalize the Auth -> Identity ownership cutover.
--
-- Existing databases MUST run scripts/cutover-auth-identity.sh --apply before
-- this migration is allowed to drop the legacy Auth-owned profile/RBAC tables.
-- Fresh databases have no users at this point and can safely continue.

DO $$
DECLARE
    account_count BIGINT;
    marker_present BOOLEAN;
    marker_complete BOOLEAN := FALSE;
BEGIN
    SELECT COUNT(*) INTO account_count
    FROM auth.user_account;

    marker_present := to_regclass('auth.identity_cutover_marker') IS NOT NULL;

    IF account_count > 0 THEN
        IF NOT marker_present THEN
            RAISE EXCEPTION
                'Auth -> Identity cutover has not been completed. Run scripts/cutover-auth-identity.sh --apply before starting auth-service.';
        END IF;

        EXECUTE
            'SELECT EXISTS (
                SELECT 1
                FROM auth.identity_cutover_marker
                WHERE id = 1
                  AND completed_at IS NOT NULL
            )'
        INTO marker_complete;

        IF NOT marker_complete THEN
            RAISE EXCEPTION
                'Auth -> Identity cutover marker is incomplete. Re-run scripts/cutover-auth-identity.sh --apply.';
        END IF;
    END IF;
END $$;

ALTER TABLE auth.user_account
    ADD COLUMN IF NOT EXISTS identity_provisioned BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF to_regclass('auth.identity_cutover_marker') IS NOT NULL THEN
        UPDATE auth.user_account
        SET identity_provisioned = TRUE
        WHERE identity_provisioned = FALSE;
    END IF;
END $$;

-- These tables are no longer owned or read by auth-service after the cutover.
-- Keep user_account because it is still the credential aggregate for now.
DROP TABLE IF EXISTS auth.role_permission;
DROP TABLE IF EXISTS auth.user_role;
DROP TABLE IF EXISTS auth.permission;
DROP TABLE IF EXISTS auth.role;
DROP TABLE IF EXISTS auth.user_profile;
