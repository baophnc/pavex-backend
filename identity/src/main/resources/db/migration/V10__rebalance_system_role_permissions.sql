-- ============================================================
-- PAVEX system RBAC policy v2
--
-- Identity is the source of truth for user roles and permissions after the
-- Auth -> Identity RBAC cutover. This migration makes the seven system roles
-- explicit and introduces read/scope-oriented permissions that later access
-- scope patches can enforce without overloading global "manage" permissions.
--
-- IMPORTANT: do not grant HUB_MANAGER global shipment permissions here.
-- shipments.cases.manage and shipments.control-tower.read are still interpreted
-- as global capabilities by shipment-service until the access-scope patch lands.
-- ============================================================

-- Make the intended data boundary visible in the role catalog shown by
-- management-web. The enforcement remains in the owning service.
UPDATE identity.role
SET name = CASE code
        WHEN 'ADMIN' THEN 'Administrator'
        WHEN 'OPERATIONS_MANAGER' THEN 'Operations Manager'
        WHEN 'HUB_MANAGER' THEN 'Hub Manager'
        WHEN 'WAREHOUSE_STAFF' THEN 'Warehouse Staff'
        WHEN 'DRIVER' THEN 'Driver'
        WHEN 'MERCHANT' THEN 'Merchant'
        WHEN 'CUSTOMER' THEN 'Customer'
        ELSE name
    END,
    description = CASE code
        WHEN 'ADMIN' THEN 'Platform administrator with global administrative access'
        WHEN 'OPERATIONS_MANAGER' THEN 'Global operations manager across the logistics network'
        WHEN 'HUB_MANAGER' THEN 'Operations manager restricted to assigned hubs'
        WHEN 'WAREHOUSE_STAFF' THEN 'Warehouse operator restricted to assigned work and hubs'
        WHEN 'DRIVER' THEN 'Courier or driver restricted to own operational assignments'
        WHEN 'MERCHANT' THEN 'Merchant user restricted to the merchant account and own shipments'
        WHEN 'CUSTOMER' THEN 'Customer user restricted to own customer-facing data'
        ELSE description
    END,
    is_system = TRUE
WHERE code IN (
    'ADMIN',
    'OPERATIONS_MANAGER',
    'HUB_MANAGER',
    'WAREHOUSE_STAFF',
    'DRIVER',
    'MERCHANT',
    'CUSTOMER'
);

-- Read/scope permissions are deliberately separate from global management
-- permissions. Some are staged for the access-scope patches that follow this
-- migration; adding them to the catalog is safe because backend authorization
-- remains deny-by-default until the owning endpoint consumes the permission.
INSERT INTO identity.permission (id, code, name, description)
VALUES
    (
        '20000000-0000-0000-0000-000000000049',
        'operations.workforce.read',
        'Read Workforce',
        'View workforce records within the actor data scope'
    ),
    (
        '20000000-0000-0000-0000-000000000050',
        'operations.assignments.read',
        'Read Operations Assignments',
        'View operational assignments within the actor data scope'
    ),
    (
        '20000000-0000-0000-0000-000000000051',
        'operations.hub.read',
        'Read Hub Operations',
        'View operational data for hubs within the actor data scope'
    ),
    (
        '20000000-0000-0000-0000-000000000052',
        'shipments.read-own',
        'Read Own Shipments',
        'View shipments owned by the authenticated merchant or customer context'
    ),
    (
        '20000000-0000-0000-0000-000000000053',
        'shipments.read-hub',
        'Read Hub Shipments',
        'View shipments currently associated with hubs in the actor data scope'
    ),
    (
        '20000000-0000-0000-0000-000000000054',
        'shipments.read-assigned',
        'Read Assigned Shipments',
        'View shipments attached to operational assignments owned by the actor'
    ),
    (
        '20000000-0000-0000-0000-000000000055',
        'shipments.exceptions.read',
        'Read Shipment Exceptions',
        'View shipment exceptions within the actor data scope'
    ),
    (
        '20000000-0000-0000-0000-000000000056',
        'shipments.cases.read',
        'Read Shipment Cases',
        'View shipment cases within the actor data scope'
    )
ON CONFLICT (code)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- System roles are backend-owned and protected by RoleService. Normalize their
-- matrix in one migration so historical incremental seeds cannot leave an
-- existing database with a different policy from a fresh database.
DELETE FROM identity.role_permission rp
USING identity.role r
WHERE rp.role_id = r.id
  AND r.code IN (
      'ADMIN',
      'OPERATIONS_MANAGER',
      'HUB_MANAGER',
      'WAREHOUSE_STAFF',
      'DRIVER',
      'MERCHANT',
      'CUSTOMER'
  );

WITH mappings(role_code, permission_code) AS (
    VALUES
    -- --------------------------------------------------------
    -- CUSTOMER: own customer-facing data only.
    -- --------------------------------------------------------
    ('CUSTOMER', 'tracking.read-own'),
    ('CUSTOMER', 'support.create'),

    -- --------------------------------------------------------
    -- MERCHANT: own merchant profile, shipments and billing.
    -- shipments.read remains during the transition because the current
    -- merchant ShipmentController still checks that permission and then
    -- enforces ownership in ShipmentQueryService. shipments.read-own is the
    -- canonical scoped capability for the next access-scope patch.
    -- --------------------------------------------------------
    ('MERCHANT', 'shipments.read'),
    ('MERCHANT', 'shipments.read-own'),
    ('MERCHANT', 'shipments.create'),
    ('MERCHANT', 'shipments.update-own'),
    ('MERCHANT', 'shipments.cancel-own'),
    ('MERCHANT', 'tracking.read-own'),
    ('MERCHANT', 'quotes.create'),
    ('MERCHANT', 'partners.merchants.manage-own'),
    ('MERCHANT', 'billing.invoices.read-own'),
    ('MERCHANT', 'billing.invoices.pay-own'),

    -- --------------------------------------------------------
    -- DRIVER: own assignments / deliveries only.
    -- --------------------------------------------------------
    ('DRIVER', 'assignments.read-own'),
    ('DRIVER', 'assignments.accept-own'),
    ('DRIVER', 'assignments.execute-own'),
    ('DRIVER', 'deliveries.complete-own'),
    ('DRIVER', 'locations.update-own'),
    ('DRIVER', 'shipments.read-assigned'),
    ('DRIVER', 'billing.cash.collect'),
    ('DRIVER', 'billing.cash.submit-own'),

    -- --------------------------------------------------------
    -- WAREHOUSE_STAFF: execution rights at assigned hubs plus own work.
    -- No global operations.*.manage permission is granted.
    -- --------------------------------------------------------
    ('WAREHOUSE_STAFF', 'network.read'),
    ('WAREHOUSE_STAFF', 'operations.hub.read'),
    ('WAREHOUSE_STAFF', 'assignments.read-own'),
    ('WAREHOUSE_STAFF', 'assignments.accept-own'),
    ('WAREHOUSE_STAFF', 'assignments.execute-own'),
    ('WAREHOUSE_STAFF', 'shipments.read-hub'),
    ('WAREHOUSE_STAFF', 'shipments.exceptions.read'),
    ('WAREHOUSE_STAFF', 'scans.create'),
    ('WAREHOUSE_STAFF', 'billing.cash.collect'),
    ('WAREHOUSE_STAFF', 'billing.cash.read-hub'),

    -- --------------------------------------------------------
    -- HUB_MANAGER: management capability is hub-scoped by Operations.
    -- Do NOT add operations.workforce.manage / assignments.manage or the
    -- global shipment case/control-tower permissions at this stage.
    -- --------------------------------------------------------
    ('HUB_MANAGER', 'network.read'),
    ('HUB_MANAGER', 'operations.workforce.read'),
    ('HUB_MANAGER', 'operations.assignments.read'),
    ('HUB_MANAGER', 'operations.hub.read'),
    ('HUB_MANAGER', 'operations.hub.manage'),
    ('HUB_MANAGER', 'shipments.read-hub'),
    ('HUB_MANAGER', 'shipments.exceptions.read'),
    ('HUB_MANAGER', 'shipments.cases.read'),
    ('HUB_MANAGER', 'billing.cash.collect'),
    ('HUB_MANAGER', 'billing.cash.read-hub'),

    -- --------------------------------------------------------
    -- OPERATIONS_MANAGER: global logistics operations, but no IAM admin and
    -- no pricing mutation. Merchant read is needed for operational context.
    -- --------------------------------------------------------
    ('OPERATIONS_MANAGER', 'partners.merchants.read'),
    ('OPERATIONS_MANAGER', 'network.read'),
    ('OPERATIONS_MANAGER', 'network.manage'),
    ('OPERATIONS_MANAGER', 'pricing.read'),
    ('OPERATIONS_MANAGER', 'operations.workforce.read'),
    ('OPERATIONS_MANAGER', 'operations.workforce.manage'),
    ('OPERATIONS_MANAGER', 'operations.assignments.read'),
    ('OPERATIONS_MANAGER', 'operations.assignments.manage'),
    ('OPERATIONS_MANAGER', 'operations.hub.read'),
    ('OPERATIONS_MANAGER', 'operations.hub.manage'),
    ('OPERATIONS_MANAGER', 'shipments.admin.read'),
    ('OPERATIONS_MANAGER', 'shipments.admin.manage'),
    ('OPERATIONS_MANAGER', 'shipments.exceptions.read'),
    ('OPERATIONS_MANAGER', 'shipments.exceptions.manage'),
    ('OPERATIONS_MANAGER', 'shipments.weight.adjust'),
    ('OPERATIONS_MANAGER', 'shipments.cases.read'),
    ('OPERATIONS_MANAGER', 'shipments.cases.manage'),
    ('OPERATIONS_MANAGER', 'shipments.control-tower.read'),
    ('OPERATIONS_MANAGER', 'call-monitoring.read'),
    ('OPERATIONS_MANAGER', 'call-monitoring.manage'),
    ('OPERATIONS_MANAGER', 'call-monitoring.reports.read'),

    -- --------------------------------------------------------
    -- ADMIN: global platform administration. Own/assigned capabilities are
    -- intentionally not required when an admin/global endpoint exists.
    -- --------------------------------------------------------
    ('ADMIN', 'identity.users.read'),
    ('ADMIN', 'identity.users.manage-profile'),
    ('ADMIN', 'identity.users.create'),
    ('ADMIN', 'identity.users.invite'),
    ('ADMIN', 'identity.users.manage-status'),
    ('ADMIN', 'identity.roles.read'),
    ('ADMIN', 'identity.roles.manage'),
    ('ADMIN', 'identity.roles.assign'),
    ('ADMIN', 'partners.merchants.read'),
    ('ADMIN', 'partners.merchants.manage-status'),
    ('ADMIN', 'network.read'),
    ('ADMIN', 'network.manage'),
    ('ADMIN', 'pricing.read'),
    ('ADMIN', 'pricing.manage'),
    ('ADMIN', 'operations.workforce.read'),
    ('ADMIN', 'operations.workforce.manage'),
    ('ADMIN', 'operations.assignments.read'),
    ('ADMIN', 'operations.assignments.manage'),
    ('ADMIN', 'operations.hub.read'),
    ('ADMIN', 'operations.hub.manage'),
    ('ADMIN', 'shipments.admin.read'),
    ('ADMIN', 'shipments.admin.manage'),
    ('ADMIN', 'shipments.exceptions.read'),
    ('ADMIN', 'shipments.exceptions.manage'),
    ('ADMIN', 'shipments.weight.adjust'),
    ('ADMIN', 'shipments.cases.read'),
    ('ADMIN', 'shipments.cases.manage'),
    ('ADMIN', 'shipments.control-tower.read'),
    ('ADMIN', 'billing.manage'),
    ('ADMIN', 'billing.cash.approve'),
    ('ADMIN', 'call-monitoring.read'),
    ('ADMIN', 'call-monitoring.manage'),
    ('ADMIN', 'call-monitoring.reports.read')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mappings m
JOIN identity.role r ON r.code = m.role_code
JOIN identity.permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

-- Existing access tokens contain an authorization snapshot. Invalidate the
-- snapshot for every user that owns a system role whose matrix was normalized.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1
    FROM identity.user_role ur
    JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code IN (
          'ADMIN',
          'OPERATIONS_MANAGER',
          'HUB_MANAGER',
          'WAREHOUSE_STAFF',
          'DRIVER',
          'MERCHANT',
          'CUSTOMER'
      )
);
