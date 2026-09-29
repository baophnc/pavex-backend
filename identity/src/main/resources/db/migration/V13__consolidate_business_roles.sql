-- Ten business roles in three groups replace the fourteen overlapping ones:
--   head office (sees every hub): ADMIN, OPERATIONS_MANAGER, COMMERCIAL_MANAGER, FINANCE_ACCOUNTANT
--   hub workforce (sees assigned hubs only): HUB_MANAGER, WAREHOUSE_OPERATOR, COURIER, LINE_HAUL_DRIVER
--   customers (own data): MERCHANT, CUSTOMER
-- "Sees every hub" becomes the scope.all-hubs permission instead of role names
-- hard-coded in each service.

INSERT INTO identity.permission (id, code, name, description)
VALUES (
    '20000000-0000-0000-0000-000000000058',
    'scope.all-hubs',
    'All Hubs Data Scope',
    'Sees and handles data of every hub instead of only the hubs the user is assigned to'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

INSERT INTO identity.role (id, code, name, description, is_system)
VALUES
    ('10000000-0000-0000-0000-000000000015', 'COMMERCIAL_MANAGER', 'Kinh doanh & CSKH', 'Duyệt shop, quản lý bảng giá và xử lý khiếu nại trên toàn quốc', TRUE),
    ('10000000-0000-0000-0000-000000000016', 'FINANCE_ACCOUNTANT', 'Kế toán', 'Duyệt tiền nộp, đối soát và hóa đơn trên toàn quốc', TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_system = TRUE;

-- Holders of a merged role keep equivalent access under the surviving role.
-- IAM_ADMIN is deliberately not promoted to ADMIN, and legacy DRIVER cannot be
-- mapped without the workforce kind: an administrator re-grants those by hand.
INSERT INTO identity.user_role (user_id, role_id, assigned_at, assigned_by)
SELECT ur.user_id, target.id, ur.assigned_at, ur.assigned_by
FROM identity.user_role ur
JOIN identity.role source ON source.id = ur.role_id
JOIN (VALUES
    ('PLATFORM_SUPER_ADMIN', 'ADMIN'),
    ('NETWORK_MANAGER', 'OPERATIONS_MANAGER'),
    ('HUB_DISPATCHER', 'HUB_MANAGER'),
    ('WAREHOUSE_STAFF', 'WAREHOUSE_OPERATOR')
) AS merged(source_code, target_code) ON merged.source_code = source.code
JOIN identity.role target ON target.code = merged.target_code
ON CONFLICT DO NOTHING;

-- user_role and role_permission rows cascade.
DELETE FROM identity.role
WHERE code IN ('PLATFORM_SUPER_ADMIN', 'IAM_ADMIN', 'NETWORK_MANAGER', 'HUB_DISPATCHER', 'WAREHOUSE_STAFF', 'DRIVER');

-- Permissions no service enforces only made the role screens harder to read.
DELETE FROM identity.permission
WHERE code IN (
    'call-monitoring.manage',
    'call-monitoring.read',
    'call-monitoring.reports.read',
    'locations.update-own',
    'quotes.create',
    'shipments.admin.manage',
    'shipments.read',
    'shipments.update-own',
    'support.create',
    'tracking.read-own'
);

UPDATE identity.role r
SET name = v.name,
    description = v.description,
    is_system = TRUE
FROM (VALUES
    ('ADMIN', 'Quản trị hệ thống', 'Toàn quyền, gồm cấp tài khoản và vai trò'),
    ('OPERATIONS_MANAGER', 'Quản lý vận hành', 'Vận đơn, sự cố, nhân sự và mạng lưới trên toàn quốc'),
    ('HUB_MANAGER', 'Trưởng bưu cục', 'Điều phối, nhân sự, vận đơn và tiền thu hộ của bưu cục được phân công'),
    ('WAREHOUSE_OPERATOR', 'Nhân viên bưu cục', 'Quét nhập/xuất kho và nhận tiền thu hộ tại bưu cục được phân công'),
    ('COURIER', 'Bưu tá', 'Lấy hàng, giao hàng và thu tiền theo việc được giao'),
    ('LINE_HAUL_DRIVER', 'Tài xế trung chuyển', 'Chạy các chuyến giữa bưu cục và kho theo việc được giao'),
    ('MERCHANT', 'Shop', 'Tạo và theo dõi vận đơn, hóa đơn của shop mình'),
    ('CUSTOMER', 'Khách cá nhân', 'Tài khoản khách hàng mặc định')
) AS v(code, name, description)
WHERE r.code = v.code;

-- ADMIN always holds the full catalog (RoleService treats it as an invariant);
-- every other system role gets exactly this matrix.
DELETE FROM identity.role_permission rp
USING identity.role r
WHERE rp.role_id = r.id
  AND r.is_system
  AND r.code <> 'ADMIN';

WITH mappings(role_code, permission_code) AS (
    VALUES
    ('OPERATIONS_MANAGER', 'scope.all-hubs'),
    ('OPERATIONS_MANAGER', 'network.read'),
    ('OPERATIONS_MANAGER', 'network.manage'),
    ('OPERATIONS_MANAGER', 'pricing.read'),
    ('OPERATIONS_MANAGER', 'partners.merchants.read'),
    ('OPERATIONS_MANAGER', 'operations.hub.read'),
    ('OPERATIONS_MANAGER', 'operations.hub.manage'),
    ('OPERATIONS_MANAGER', 'operations.workforce.read'),
    ('OPERATIONS_MANAGER', 'operations.workforce.manage'),
    ('OPERATIONS_MANAGER', 'operations.assignments.read'),
    ('OPERATIONS_MANAGER', 'operations.assignments.manage'),
    ('OPERATIONS_MANAGER', 'operations.dispatch.manage'),
    ('OPERATIONS_MANAGER', 'shipments.admin.read'),
    ('OPERATIONS_MANAGER', 'shipments.exceptions.read'),
    ('OPERATIONS_MANAGER', 'shipments.exceptions.manage'),
    ('OPERATIONS_MANAGER', 'shipments.cases.read'),
    ('OPERATIONS_MANAGER', 'shipments.cases.manage'),
    ('OPERATIONS_MANAGER', 'shipments.control-tower.read'),
    ('OPERATIONS_MANAGER', 'shipments.weight.adjust'),

    ('COMMERCIAL_MANAGER', 'scope.all-hubs'),
    ('COMMERCIAL_MANAGER', 'network.read'),
    ('COMMERCIAL_MANAGER', 'pricing.read'),
    ('COMMERCIAL_MANAGER', 'pricing.manage'),
    ('COMMERCIAL_MANAGER', 'partners.merchants.read'),
    ('COMMERCIAL_MANAGER', 'partners.merchants.manage-status'),
    ('COMMERCIAL_MANAGER', 'shipments.admin.read'),
    ('COMMERCIAL_MANAGER', 'shipments.exceptions.read'),
    ('COMMERCIAL_MANAGER', 'shipments.cases.read'),
    ('COMMERCIAL_MANAGER', 'shipments.cases.manage'),

    ('FINANCE_ACCOUNTANT', 'scope.all-hubs'),
    ('FINANCE_ACCOUNTANT', 'billing.manage'),
    ('FINANCE_ACCOUNTANT', 'billing.cash.approve'),
    ('FINANCE_ACCOUNTANT', 'billing.cash.read-hub'),
    ('FINANCE_ACCOUNTANT', 'partners.merchants.read'),
    ('FINANCE_ACCOUNTANT', 'shipments.admin.read'),

    ('HUB_MANAGER', 'operations.hub.read'),
    ('HUB_MANAGER', 'operations.hub.manage'),
    ('HUB_MANAGER', 'operations.workforce.read'),
    ('HUB_MANAGER', 'operations.assignments.read'),
    ('HUB_MANAGER', 'operations.dispatch.manage'),
    ('HUB_MANAGER', 'shipments.read-hub'),
    ('HUB_MANAGER', 'shipments.exceptions.read'),
    ('HUB_MANAGER', 'shipments.exceptions.manage'),
    ('HUB_MANAGER', 'shipments.cases.read'),
    ('HUB_MANAGER', 'shipments.cases.manage'),
    ('HUB_MANAGER', 'shipments.control-tower.read'),
    ('HUB_MANAGER', 'shipments.weight.adjust'),
    ('HUB_MANAGER', 'scans.create'),
    ('HUB_MANAGER', 'billing.cash.collect'),
    ('HUB_MANAGER', 'billing.cash.read-hub'),

    ('WAREHOUSE_OPERATOR', 'operations.hub.read'),
    ('WAREHOUSE_OPERATOR', 'assignments.read-own'),
    ('WAREHOUSE_OPERATOR', 'assignments.accept-own'),
    ('WAREHOUSE_OPERATOR', 'assignments.execute-own'),
    ('WAREHOUSE_OPERATOR', 'shipments.read-hub'),
    ('WAREHOUSE_OPERATOR', 'shipments.exceptions.read'),
    ('WAREHOUSE_OPERATOR', 'scans.create'),
    ('WAREHOUSE_OPERATOR', 'billing.cash.collect'),
    ('WAREHOUSE_OPERATOR', 'billing.cash.read-hub'),

    ('COURIER', 'assignments.read-own'),
    ('COURIER', 'assignments.accept-own'),
    ('COURIER', 'assignments.execute-own'),
    ('COURIER', 'deliveries.complete-own'),
    ('COURIER', 'shipments.read-assigned'),
    ('COURIER', 'scans.create'),
    ('COURIER', 'billing.cash.collect'),
    ('COURIER', 'billing.cash.submit-own'),

    ('LINE_HAUL_DRIVER', 'assignments.read-own'),
    ('LINE_HAUL_DRIVER', 'assignments.accept-own'),
    ('LINE_HAUL_DRIVER', 'assignments.execute-own'),
    ('LINE_HAUL_DRIVER', 'shipments.read-assigned'),
    ('LINE_HAUL_DRIVER', 'scans.create'),

    ('MERCHANT', 'partners.merchants.manage-own'),
    ('MERCHANT', 'shipments.create'),
    ('MERCHANT', 'shipments.read-own'),
    ('MERCHANT', 'shipments.cancel-own'),
    ('MERCHANT', 'billing.invoices.read-own'),
    ('MERCHANT', 'billing.invoices.pay-own')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mappings m
JOIN identity.role r ON r.code = m.role_code
JOIN identity.permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Every issued token predates this catalog.
UPDATE identity.user_account
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1;
