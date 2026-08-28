-- Auth now owns credentials/session state only. User lifecycle/profile state is
-- owned by identity-service after the mandatory V15 RBAC cutover.
ALTER TABLE auth.user_account
    DROP COLUMN IF EXISTS profile_status,
    DROP COLUMN IF EXISTS status;
