-- Accounts an administrator creates start with a temporary password: the holder must choose a new
-- password at the first sign-in, and the temporary one stops working after it expires.
ALTER TABLE auth.user_account
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN temporary_password_expires_at TIMESTAMPTZ NULL;
