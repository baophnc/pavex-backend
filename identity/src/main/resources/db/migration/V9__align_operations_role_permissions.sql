-- operations.hub.manage already exists in the platform permission catalog.
-- HUB_MANAGER had it from the original seed, but the platform ADMIN and the
-- global OPERATIONS_MANAGER were missing it. Operations is now a standalone
-- service, so align the role matrix before its admin APIs are introduced.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'operations.hub.manage'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
ON CONFLICT DO NOTHING;

-- Existing access tokens carry their authorization snapshot. Bump the version
-- for affected users so fresh tokens/authorization contexts reflect the role
-- matrix change immediately after re-authentication/refresh policy permits it.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW()
WHERE EXISTS (
    SELECT 1
    FROM identity.user_role ur
    JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
);
