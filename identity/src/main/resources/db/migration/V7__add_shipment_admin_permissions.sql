INSERT INTO identity.permission (id, code, name, description)
VALUES
    (
        '20000000-0000-0000-0000-000000000046',
        'shipments.admin.read',
        'Read All Shipments',
        'View shipment operations, parcel snapshots and shipment timelines across merchants'
    ),
    (
        '20000000-0000-0000-0000-000000000047',
        'shipments.admin.manage',
        'Manage Shipments',
        'Manage shipment lifecycle and administrative shipment actions'
    )
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'shipments.admin.read'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'shipments.admin.manage'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER')
ON CONFLICT DO NOTHING;

-- Keep the platform administrator genuinely complete for the existing shipment
-- permissions that were seeded before the shipment-service was split out.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code IN (
    'shipments.read',
    'shipments.create',
    'shipments.update-own',
    'shipments.cancel-own',
    'shipments.exceptions.manage',
    'shipments.weight.adjust',
    'shipments.cases.manage',
    'shipments.control-tower.read',
    'shipments.admin.read',
    'shipments.admin.manage'
)
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
