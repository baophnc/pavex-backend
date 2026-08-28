INSERT INTO identity.permission (
    id,
    code,
    name,
    description
)
VALUES (
    '20000000-0000-0000-0000-000000000043',
    'network.read',
    'Read Network',
    'View network regions, hubs, service areas, coverages and audit records'
)
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'network.read'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
ON CONFLICT DO NOTHING;

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
