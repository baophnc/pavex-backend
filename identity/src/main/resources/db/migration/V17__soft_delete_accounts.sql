-- Accounts are deleted softly: the account stays (shipments, history and audit keep pointing at it),
-- but it cannot sign in, disappears from the default lists, and its email is replaced by a placeholder
-- so the real address can be used again. The original address is kept in deleted_email.
ALTER TABLE identity.user_account
    DROP CONSTRAINT ck_identity_user_status,
    ADD CONSTRAINT ck_identity_user_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED', 'DELETED')),
    ADD COLUMN deleted_at TIMESTAMPTZ NULL,
    ADD COLUMN deleted_by_user_id UUID NULL,
    ADD COLUMN deleted_email VARCHAR(255) NULL;

INSERT INTO identity.permission (id, code, name, description)
VALUES ('20000000-0000-0000-0000-000000000065', 'identity.users.delete', 'Delete Users',
        'Deletes an account softly: it can no longer sign in and its email can be used again')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'identity.users.delete'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Tokens issued before this migration do not carry the new permission.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1, updated_at = NOW(), version = version + 1
WHERE EXISTS (SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.code = 'ADMIN');
