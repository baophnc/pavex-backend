INSERT INTO identity.permission (id, code, name, description)
VALUES
    (
        '20000000-0000-0000-0000-000000000044',
        'pricing.read',
        'Read Pricing',
        'View pricing rate plans and rate rules'
    ),
    (
        '20000000-0000-0000-0000-000000000045',
        'pricing.manage',
        'Manage Pricing',
        'Create and edit draft pricing rate plans and rate rules'
    )
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'pricing.read'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'pricing.manage'
WHERE r.code = 'ADMIN'
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
