-- A small company: there is no separate sales/support or accounting department. Their work moves to
-- the hubs, where staff do it and the hub manager approves, and the operations manager (the company's
-- head) sees and approves everything. COMMERCIAL_MANAGER and FINANCE_ACCOUNTANT are removed.
--
-- Hub managers and staff see merchants of their own hub only (Partners scopes the lists by the hub
-- serving each merchant's warehouse). Support requests come from the public contact form and have no
-- hub, so they are one shared company inbox.
WITH grants(role_code, permission_code) AS (
    VALUES
    -- The operations manager runs the company.
    ('OPERATIONS_MANAGER', 'partners.merchants.manage-status'),
    ('OPERATIONS_MANAGER', 'pricing.manage'),
    ('OPERATIONS_MANAGER', 'support.requests.read'),
    ('OPERATIONS_MANAGER', 'support.requests.manage'),
    ('OPERATIONS_MANAGER', 'billing.cash.read-hub'),
    ('OPERATIONS_MANAGER', 'billing.cash.approve'),
    ('OPERATIONS_MANAGER', 'billing.manage'),
    ('OPERATIONS_MANAGER', 'shipments.counter.create'),
    ('OPERATIONS_MANAGER', 'billing.counter.settle'),
    ('OPERATIONS_MANAGER', 'identity.users.read'),
    ('OPERATIONS_MANAGER', 'identity.users.create'),
    ('OPERATIONS_MANAGER', 'identity.users.invite'),
    ('OPERATIONS_MANAGER', 'identity.users.manage-profile'),
    ('OPERATIONS_MANAGER', 'identity.users.manage-status'),
    ('OPERATIONS_MANAGER', 'identity.roles.read'),
    ('OPERATIONS_MANAGER', 'identity.roles.assign'),
    -- Hub managers approve the shops of their hub and answer customers.
    ('HUB_MANAGER', 'partners.merchants.read'),
    ('HUB_MANAGER', 'partners.merchants.manage-status'),
    ('HUB_MANAGER', 'support.requests.read'),
    ('HUB_MANAGER', 'support.requests.manage'),
    -- Hub staff take shop applications and support requests.
    ('WAREHOUSE_OPERATOR', 'partners.merchants.read'),
    ('WAREHOUSE_OPERATOR', 'support.requests.read'),
    ('WAREHOUSE_OPERATOR', 'support.requests.manage')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM grants g
JOIN identity.role r ON r.code = g.role_code
JOIN identity.permission p ON p.code = g.permission_code
ON CONFLICT DO NOTHING;

-- Anyone who held a removed role was head office: they become operations managers.
INSERT INTO identity.user_role (user_id, role_id, assigned_at, assigned_by)
SELECT DISTINCT ur.user_id, target.id, NOW(), NULL::uuid
FROM identity.user_role ur
JOIN identity.role removed ON removed.id = ur.role_id AND removed.code IN ('COMMERCIAL_MANAGER', 'FINANCE_ACCOUNTANT')
JOIN identity.role target ON target.code = 'OPERATIONS_MANAGER'
ON CONFLICT DO NOTHING;

-- Tokens issued before this migration carry the old roles and permissions.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1, updated_at = NOW(), version = version + 1
WHERE EXISTS (
    SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code IN ('COMMERCIAL_MANAGER', 'FINANCE_ACCOUNTANT', 'OPERATIONS_MANAGER', 'HUB_MANAGER', 'WAREHOUSE_OPERATOR')
);

DELETE FROM identity.user_role WHERE role_id IN (
    SELECT id FROM identity.role WHERE code IN ('COMMERCIAL_MANAGER', 'FINANCE_ACCOUNTANT')
);
DELETE FROM identity.role_permission WHERE role_id IN (
    SELECT id FROM identity.role WHERE code IN ('COMMERCIAL_MANAGER', 'FINANCE_ACCOUNTANT')
);
DELETE FROM identity.role WHERE code IN ('COMMERCIAL_MANAGER', 'FINANCE_ACCOUNTANT');

-- What each remaining role is, in words a hub manager understands.
UPDATE identity.role SET name = 'Quản trị hệ thống',
    description = 'Cài đặt kỹ thuật: tài khoản, vai trò, mạng lưới, bảng giá. Không dùng cho việc hằng ngày.'
WHERE code = 'ADMIN';
UPDATE identity.role SET name = 'Quản lý vận hành',
    description = 'Người điều hành công ty: xem mọi bưu cục, duyệt cửa hàng, tiền và đối soát.'
WHERE code = 'OPERATIONS_MANAGER';
UPDATE identity.role SET name = 'Trưởng bưu cục',
    description = 'Quản lý một bưu cục: duyệt việc nhân viên làm, cửa hàng và tiền của bưu cục mình.'
WHERE code = 'HUB_MANAGER';
UPDATE identity.role SET name = 'Nhân viên bưu cục',
    description = 'Làm việc tại bưu cục: nhập kho, nhận gửi tại quầy, kiểm tiền, chăm sóc khách và cửa hàng.'
WHERE code = 'WAREHOUSE_OPERATOR';
UPDATE identity.role SET name = 'Bưu tá',
    description = 'Đi lấy hàng và giao hàng bằng app, thu tiền rồi nộp về bưu cục.'
WHERE code = 'COURIER';
UPDATE identity.role SET name = 'Tài xế trung chuyển',
    description = 'Chạy xe giữa các bưu cục bằng app, có ghi vị trí xe khi chạy.'
WHERE code = 'LINE_HAUL_DRIVER';
UPDATE identity.role SET name = 'Cửa hàng',
    description = 'Shop đã được duyệt: tạo đơn trên web, xem đối soát và hoá đơn.'
WHERE code = 'MERCHANT';
UPDATE identity.role SET name = 'Khách hàng',
    description = 'Người dùng tự đăng ký trên web: tra cứu, gửi hỗ trợ, đăng ký mở shop.'
WHERE code = 'CUSTOMER';
