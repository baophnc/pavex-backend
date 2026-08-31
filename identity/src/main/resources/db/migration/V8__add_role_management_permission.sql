-- Management Patch 8: allow authorized administrators to manage custom roles.
-- Permission definitions remain backend-owned/read-only; this capability only
-- controls custom-role metadata and role -> permission assignments.
INSERT INTO identity.permission (
    id,
    code,
    name,
    description
)
VALUES (
    '20000000-0000-0000-0000-000000000048',
    'identity.roles.manage',
    'Manage Roles',
    'Create, update and delete custom roles and manage their permission matrix'
)
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'identity.roles.manage'
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Existing ADMIN access tokens are intentionally short-lived, but incrementing
-- authorization_version ensures newly issued tokens/auth contexts reflect the
-- catalog change and keeps the authorization state monotonic.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1
    FROM identity.user_role ur
    JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code = 'ADMIN'
);
