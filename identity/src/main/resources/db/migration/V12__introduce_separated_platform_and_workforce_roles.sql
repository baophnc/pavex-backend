-- Introduce explicit platform, IAM, network, dispatch and execution roles while
-- keeping the original seven roles available during the rolling migration.
INSERT INTO identity.role (id, code, name, description, is_system)
VALUES
    ('10000000-0000-0000-0000-000000000008', 'PLATFORM_SUPER_ADMIN', 'Platform Super Admin', 'Break-glass platform administrator with every catalog capability', TRUE),
    ('10000000-0000-0000-0000-000000000009', 'IAM_ADMIN', 'IAM Administrator', 'Manages identities, users, roles and role assignments', TRUE),
    ('10000000-0000-0000-0000-000000000010', 'NETWORK_MANAGER', 'Network Manager', 'Manages hubs, transport lanes, schedules, routes and capacity', TRUE),
    ('10000000-0000-0000-0000-000000000011', 'HUB_DISPATCHER', 'Hub Dispatcher', 'Dispatches operational work within assigned hubs', TRUE),
    ('10000000-0000-0000-0000-000000000012', 'WAREHOUSE_OPERATOR', 'Warehouse Operator', 'Executes warehouse handling and scans within assigned hubs', TRUE),
    ('10000000-0000-0000-0000-000000000013', 'LINE_HAUL_DRIVER', 'Line-haul Driver', 'Executes assigned transport legs between hubs', TRUE),
    ('10000000-0000-0000-0000-000000000014', 'COURIER', 'Courier', 'Executes assigned pickup and last-mile delivery work', TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_system = TRUE;

INSERT INTO identity.permission (id, code, name, description)
VALUES (
    '20000000-0000-0000-0000-000000000057',
    'operations.dispatch.manage',
    'Dispatch Hub Assignments',
    'Assign, reassign and cancel operational work within the actor hub scope'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- A platform super administrator is the only new role that intentionally owns
-- the complete permission catalog. RoleService also treats it as an invariant
-- so new catalog entries are effective after token refresh.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'PLATFORM_SUPER_ADMIN'
ON CONFLICT DO NOTHING;

WITH mappings(role_code, permission_code) AS (
    VALUES
    ('IAM_ADMIN', 'identity.users.read'),
    ('IAM_ADMIN', 'identity.users.manage-profile'),
    ('IAM_ADMIN', 'identity.users.create'),
    ('IAM_ADMIN', 'identity.users.invite'),
    ('IAM_ADMIN', 'identity.users.manage-status'),
    ('IAM_ADMIN', 'identity.roles.read'),
    ('IAM_ADMIN', 'identity.roles.manage'),
    ('IAM_ADMIN', 'identity.roles.assign'),

    ('NETWORK_MANAGER', 'network.read'),
    ('NETWORK_MANAGER', 'network.manage'),
    ('NETWORK_MANAGER', 'pricing.read'),

    ('HUB_DISPATCHER', 'network.read'),
    ('HUB_DISPATCHER', 'operations.assignments.read'),
    ('HUB_DISPATCHER', 'operations.dispatch.manage'),
    ('HUB_DISPATCHER', 'operations.hub.read'),
    ('HUB_DISPATCHER', 'shipments.read-hub'),
    ('HUB_DISPATCHER', 'shipments.exceptions.read'),
    ('HUB_DISPATCHER', 'shipments.cases.read'),
    ('HUB_DISPATCHER', 'shipments.control-tower.read'),

    ('WAREHOUSE_OPERATOR', 'network.read'),
    ('WAREHOUSE_OPERATOR', 'operations.hub.read'),
    ('WAREHOUSE_OPERATOR', 'assignments.read-own'),
    ('WAREHOUSE_OPERATOR', 'assignments.accept-own'),
    ('WAREHOUSE_OPERATOR', 'assignments.execute-own'),
    ('WAREHOUSE_OPERATOR', 'shipments.read-hub'),
    ('WAREHOUSE_OPERATOR', 'shipments.exceptions.read'),
    ('WAREHOUSE_OPERATOR', 'scans.create'),
    ('WAREHOUSE_OPERATOR', 'billing.cash.collect'),
    ('WAREHOUSE_OPERATOR', 'billing.cash.read-hub'),

    ('LINE_HAUL_DRIVER', 'assignments.read-own'),
    ('LINE_HAUL_DRIVER', 'assignments.accept-own'),
    ('LINE_HAUL_DRIVER', 'assignments.execute-own'),
    ('LINE_HAUL_DRIVER', 'locations.update-own'),
    ('LINE_HAUL_DRIVER', 'shipments.read-assigned'),
    ('LINE_HAUL_DRIVER', 'scans.create'),
    ('LINE_HAUL_DRIVER', 'billing.cash.collect'),
    ('LINE_HAUL_DRIVER', 'billing.cash.submit-own'),

    ('COURIER', 'assignments.read-own'),
    ('COURIER', 'assignments.accept-own'),
    ('COURIER', 'assignments.execute-own'),
    ('COURIER', 'deliveries.complete-own'),
    ('COURIER', 'locations.update-own'),
    ('COURIER', 'shipments.read-assigned'),
    ('COURIER', 'scans.create'),
    ('COURIER', 'billing.cash.collect'),
    ('COURIER', 'billing.cash.submit-own'),

    -- Transitional roles remain executable while existing workforce accounts
    -- are migrated to COURIER or LINE_HAUL_DRIVER.
    ('DRIVER', 'scans.create'),
    ('ADMIN', 'operations.dispatch.manage'),
    ('OPERATIONS_MANAGER', 'operations.dispatch.manage'),
    ('HUB_MANAGER', 'operations.dispatch.manage')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mappings m
JOIN identity.role r ON r.code = m.role_code
JOIN identity.permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1
    FROM identity.user_role ur
    JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code IN ('ADMIN', 'OPERATIONS_MANAGER', 'HUB_MANAGER', 'DRIVER')
);
