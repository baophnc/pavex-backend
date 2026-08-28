-- ============================================================
-- PAVEX Identity RBAC catalog
-- ============================================================

-- ============================================================
-- Roles
-- ============================================================

INSERT INTO identity.role (
    id,
    code,
    name,
    description,
    is_system
)
VALUES
(
    '10000000-0000-0000-0000-000000000002',
    'ADMIN',
    'Administrator',
    'Platform administrator',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000003',
    'OPERATIONS_MANAGER',
    'Operations Manager',
    'Operations manager',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000004',
    'HUB_MANAGER',
    'Hub Manager',
    'Hub manager',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000005',
    'WAREHOUSE_STAFF',
    'Warehouse Staff',
    'Warehouse staff',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000006',
    'DRIVER',
    'Driver',
    'Driver',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000007',
    'MERCHANT',
    'Merchant',
    'Merchant',
    TRUE
),
(
    '10000000-0000-0000-0000-000000000001',
    'CUSTOMER',
    'Customer',
    'Default Pavex customer account',
    TRUE
)
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_system = EXCLUDED.is_system;


-- ============================================================
-- Permissions
-- ============================================================

INSERT INTO identity.permission (
    id,
    code,
    name,
    description
)
VALUES

('20000000-0000-0000-0000-000000000001',
 'tracking.read-own',
 'Read Own Tracking',
 'Read own tracking'),

('20000000-0000-0000-0000-000000000002',
 'support.create',
 'Create Support',
 'Create support tickets'),

('20000000-0000-0000-0000-000000000003',
 'shipments.read',
 'Read Shipments',
 'Read shipments'),

('20000000-0000-0000-0000-000000000004',
 'shipments.create',
 'Create Shipments',
 'Create shipments'),

('20000000-0000-0000-0000-000000000005',
 'shipments.update-own',
 'Update Own Shipments',
 'Update own shipments before pickup'),

('20000000-0000-0000-0000-000000000006',
 'shipments.cancel-own',
 'Cancel Own Shipments',
 'Cancel own shipments'),

('20000000-0000-0000-0000-000000000007',
 'shipments.exceptions.manage',
 'Manage Shipment Exceptions',
 'Manage shipment exception workflows'),

('20000000-0000-0000-0000-000000000008',
 'shipments.weight.adjust',
 'Adjust Shipment Weight',
 'Adjust parcel weights'),

('20000000-0000-0000-0000-000000000009',
 'shipments.cases.manage',
 'Manage Shipment Cases',
 'Manage shipment operational cases'),

('20000000-0000-0000-0000-000000000010',
 'shipments.control-tower.read',
 'Read Shipment Control Tower',
 'Read shipment control tower alerts'),

('20000000-0000-0000-0000-000000000011',
 'assignments.read-own',
 'Read Own Assignments',
 'Read own assignments'),

('20000000-0000-0000-0000-000000000012',
 'assignments.accept-own',
 'Accept Own Assignments',
 'Accept own assignments'),

('20000000-0000-0000-0000-000000000013',
 'assignments.execute-own',
 'Execute Own Assignments',
 'Execute own operational assignments'),

('20000000-0000-0000-0000-000000000014',
 'deliveries.complete-own',
 'Complete Own Deliveries',
 'Complete own deliveries'),

('20000000-0000-0000-0000-000000000015',
 'locations.update-own',
 'Update Own Location',
 'Update own location'),

('20000000-0000-0000-0000-000000000016',
 'quotes.create',
 'Create Quotes',
 'Create pricing quotes'),

('20000000-0000-0000-0000-000000000017',
 'billing.invoices.read-own',
 'Read Own Invoices',
 'Read own customer invoices'),

('20000000-0000-0000-0000-000000000018',
 'billing.invoices.pay-own',
 'Pay Own Invoices',
 'Pay own customer invoices'),

('20000000-0000-0000-0000-000000000019',
 'billing.manage',
 'Manage Billing',
 'Manage customer invoices and payments'),

('20000000-0000-0000-0000-000000000020',
 'billing.cash.collect',
 'Collect Cash',
 'Collect cash for customer invoices'),

('20000000-0000-0000-0000-000000000021',
 'billing.cash.submit-own',
 'Submit Own Cash',
 'Submit own courier cash remittances'),

('20000000-0000-0000-0000-000000000022',
 'billing.cash.read-hub',
 'Read Hub Cash',
 'Read cash held at assigned hubs'),

('20000000-0000-0000-0000-000000000023',
 'billing.cash.approve',
 'Approve Cash',
 'Approve or reject cash collections and remittances'),

('20000000-0000-0000-0000-000000000024',
 'call-monitoring.read',
 'Read Call Monitoring',
 'Read call monitoring records'),

('20000000-0000-0000-0000-000000000025',
 'call-monitoring.manage',
 'Manage Call Monitoring',
 'Manage call lifecycle and supervisor reviews'),

('20000000-0000-0000-0000-000000000026',
 'call-monitoring.reports.read',
 'Read Call Reports',
 'Read call monitoring reports'),

('20000000-0000-0000-0000-000000000027',
 'network.manage',
 'Manage Network',
 'Manage logistics network'),

('20000000-0000-0000-0000-000000000028',
 'operations.workforce.manage',
 'Manage Workforce',
 'Manage operations workforce and hub memberships'),

('20000000-0000-0000-0000-000000000029',
 'operations.assignments.manage',
 'Manage Assignments',
 'Manage operational assignments'),

('20000000-0000-0000-0000-000000000030',
 'operations.hub.manage',
 'Manage Hub',
 'Manage workforce shifts and assignments within hubs'),

('20000000-0000-0000-0000-000000000031',
 'scans.create',
 'Create Scans',
 'Create parcel scans'),

('20000000-0000-0000-0000-000000000032',
 'identity.users.read',
 'Read Users',
 'Read users'),

('20000000-0000-0000-0000-000000000033',
 'identity.users.manage-profile',
 'Manage User Profiles',
 'Manage user profiles'),

('20000000-0000-0000-0000-000000000034',
 'identity.roles.read',
 'Read Roles',
 'Read roles'),

('20000000-0000-0000-0000-000000000035',
 'identity.users.create',
 'Create Users',
 'Create users'),

('20000000-0000-0000-0000-000000000036',
 'identity.users.invite',
 'Invite Users',
 'Invite users'),

('20000000-0000-0000-0000-000000000037',
 'identity.roles.assign',
 'Assign Roles',
 'Assign roles to users'),

('20000000-0000-0000-0000-000000000038',
 'identity.users.manage-status',
 'Manage User Status',
 'Manage user account status'),

('20000000-0000-0000-0000-000000000039',
 'partners.merchants.manage-own',
 'Manage Own Merchant',
 'Manage own merchant profile'),

('20000000-0000-0000-0000-000000000040',
 'partners.merchants.read',
 'Read Merchants',
 'Read merchant profiles'),

('20000000-0000-0000-0000-000000000041',
 'partners.merchants.manage-status',
 'Manage Merchant Status',
 'Manage merchant verification and status')

ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;


-- ============================================================
-- Role -> Permission
-- ============================================================

WITH mappings(role_code, permission_code) AS (
    VALUES

    -- CUSTOMER
    ('CUSTOMER', 'tracking.read-own'),
    ('CUSTOMER', 'support.create'),

    -- DRIVER
    ('DRIVER', 'assignments.read-own'),
    ('DRIVER', 'assignments.accept-own'),
    ('DRIVER', 'assignments.execute-own'),
    ('DRIVER', 'deliveries.complete-own'),
    ('DRIVER', 'locations.update-own'),
    ('DRIVER', 'billing.cash.collect'),
    ('DRIVER', 'billing.cash.submit-own'),

    -- WAREHOUSE STAFF
    ('WAREHOUSE_STAFF', 'scans.create'),
    ('WAREHOUSE_STAFF', 'assignments.read-own'),
    ('WAREHOUSE_STAFF', 'assignments.accept-own'),
    ('WAREHOUSE_STAFF', 'assignments.execute-own'),
    ('WAREHOUSE_STAFF', 'billing.cash.collect'),
    ('WAREHOUSE_STAFF', 'billing.cash.read-hub'),

    -- MERCHANT
    ('MERCHANT', 'shipments.read'),
    ('MERCHANT', 'shipments.create'),
    ('MERCHANT', 'shipments.update-own'),
    ('MERCHANT', 'shipments.cancel-own'),
    ('MERCHANT', 'tracking.read-own'),
    ('MERCHANT', 'quotes.create'),
    ('MERCHANT', 'partners.merchants.manage-own'),
    ('MERCHANT', 'billing.invoices.read-own'),
    ('MERCHANT', 'billing.invoices.pay-own'),

    -- HUB MANAGER
    ('HUB_MANAGER', 'operations.hub.manage'),
    ('HUB_MANAGER', 'billing.cash.collect'),
    ('HUB_MANAGER', 'billing.cash.read-hub'),

    -- OPERATIONS MANAGER
    ('OPERATIONS_MANAGER', 'network.manage'),
    ('OPERATIONS_MANAGER', 'operations.workforce.manage'),
    ('OPERATIONS_MANAGER', 'operations.assignments.manage'),
    ('OPERATIONS_MANAGER', 'shipments.exceptions.manage'),
    ('OPERATIONS_MANAGER', 'shipments.weight.adjust'),
    ('OPERATIONS_MANAGER', 'shipments.cases.manage'),
    ('OPERATIONS_MANAGER', 'shipments.control-tower.read'),
    ('OPERATIONS_MANAGER', 'call-monitoring.read'),
    ('OPERATIONS_MANAGER', 'call-monitoring.manage'),
    ('OPERATIONS_MANAGER', 'call-monitoring.reports.read'),

    -- ADMIN
    ('ADMIN', 'identity.users.invite'),
    ('ADMIN', 'identity.users.create'),
    ('ADMIN', 'identity.roles.read'),
    ('ADMIN', 'identity.users.read'),
    ('ADMIN', 'identity.users.manage-profile'),
    ('ADMIN', 'identity.roles.assign'),
    ('ADMIN', 'identity.users.manage-status'),

    ('ADMIN', 'partners.merchants.read'),
    ('ADMIN', 'partners.merchants.manage-status'),

    ('ADMIN', 'network.manage'),

    ('ADMIN', 'operations.workforce.manage'),
    ('ADMIN', 'operations.assignments.manage'),

    ('ADMIN', 'shipments.exceptions.manage'),
    ('ADMIN', 'shipments.weight.adjust'),
    ('ADMIN', 'shipments.cases.manage'),
    ('ADMIN', 'shipments.control-tower.read'),

    ('ADMIN', 'billing.manage'),
    ('ADMIN', 'billing.cash.approve'),

    ('ADMIN', 'call-monitoring.read'),
    ('ADMIN', 'call-monitoring.manage'),
    ('ADMIN', 'call-monitoring.reports.read')
)

INSERT INTO identity.role_permission (
    role_id,
    permission_id
)
SELECT
    r.id,
    p.id
FROM mappings m
JOIN identity.role r
    ON r.code = m.role_code
JOIN identity.permission p
    ON p.code = m.permission_code
ON CONFLICT DO NOTHING;


-- Migrated V1 identities did not have RBAC tables yet. Give them the
-- default CUSTOMER role so auth-context is never silently empty.
INSERT INTO identity.user_role (user_id, role_id, assigned_at, assigned_by)
SELECT
    u.id,
    r.id,
    u.created_at,
    NULL
FROM identity.user_account u
JOIN identity.role r ON r.code = 'CUSTOMER'
WHERE NOT EXISTS (
    SELECT 1
    FROM identity.user_role ur
    WHERE ur.user_id = u.id
)
ON CONFLICT DO NOTHING;
