-- Tài khoản test (@pavex.test, kể cả tài khoản test đã xoá mềm). Giữ lại admin@pavex.com và tài khoản riêng của bạn (ví dụ Gmail),
-- nhưng gỡ vai trò MERCHANT của chúng vì mọi cửa hàng được tạo lại từ đầu.
\set ON_ERROR_STOP on
BEGIN;

DELETE FROM identity.user_account
WHERE email LIKE '%@pavex.test'
   OR email LIKE '%@demo.pavex.local'
   OR email LIKE '%@khachhang.pavex.com'
   OR email LIKE 'deleted+%@pavex.invalid'
   OR (email LIKE '%@pavex.com' AND email <> 'admin@pavex.com');

UPDATE identity.user_account SET authorization_version = authorization_version + 1, updated_at = NOW()
WHERE id IN (SELECT ur.user_id FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id WHERE r.code = 'MERCHANT');
DELETE FROM identity.user_role WHERE role_id = (SELECT id FROM identity.role WHERE code = 'MERCHANT');

CREATE TEMP TABLE seed_user (
    id UUID, email TEXT, first_name TEXT, last_name TEXT, gender TEXT, date_of_birth DATE, roles TEXT[]
) ON COMMIT DROP;

INSERT INTO seed_user VALUES
    ('5eed0010-0000-0000-0000-000000000001', 'admin@pavex.test',        'Quản Trị',  'Lê',     'MALE',   '1988-03-12', ARRAY['ADMIN']),
    ('5eed0010-0000-0000-0000-000000000002', 'vanhanh@pavex.test',      'Văn Hành',  'Phạm',   'MALE',   '1985-07-21', ARRAY['OPERATIONS_MANAGER']),
    ('5eed0010-0000-0000-0000-000000000003', 'taichinh@pavex.test',     'Thu Hà',    'Nguyễn', 'FEMALE', '1990-11-02', ARRAY['FINANCE_ACCOUNTANT']),
    ('5eed0010-0000-0000-0000-000000000004', 'cskh@pavex.test',         'Minh Anh',  'Trần',   'FEMALE', '1995-01-30', ARRAY['COMMERCIAL_MANAGER']),
    ('5eed0010-0000-0000-0000-000000000011', 'truongbc.hn@pavex.test',  'Đức Thắng', 'Hoàng',  'MALE',   '1982-05-09', ARRAY['HUB_MANAGER']),
    ('5eed0010-0000-0000-0000-000000000012', 'nhanvien.hn@pavex.test',  'Thị Lan',   'Vũ',     'FEMALE', '1996-08-17', ARRAY['WAREHOUSE_OPERATOR']),
    ('5eed0010-0000-0000-0000-000000000013', 'buuta.hn@pavex.test',     'Văn Nam',   'Đỗ',     'MALE',   '1998-02-25', ARRAY['COURIER']),
    ('5eed0010-0000-0000-0000-000000000021', 'truongbc.hcm@pavex.test', 'Thanh Tâm', 'Huỳnh',  'FEMALE', '1984-09-14', ARRAY['HUB_MANAGER']),
    ('5eed0010-0000-0000-0000-000000000022', 'nhanvien.hcm@pavex.test', 'Quốc Bảo',  'Võ',     'MALE',   '1997-12-03', ARRAY['WAREHOUSE_OPERATOR']),
    ('5eed0010-0000-0000-0000-000000000023', 'buuta.hcm@pavex.test',    'Minh Tú',   'Trương', 'MALE',   '1999-04-19', ARRAY['COURIER']),
    ('5eed0010-0000-0000-0000-000000000031', 'taixe@pavex.test',        'Văn Hùng',  'Lâm',    'MALE',   '1980-06-28', ARRAY['LINE_HAUL_DRIVER']),
    ('5eed0010-0000-0000-0000-000000000041', 'shop.daduyet@pavex.test', 'Ngọc Mai',  'Bùi',    'FEMALE', '1993-10-10', ARRAY['CUSTOMER', 'MERCHANT']),
    ('5eed0010-0000-0000-0000-000000000042', 'shop.choduyet@pavex.test','Hữu Phước', 'Đặng',   'MALE',   '1991-03-08', ARRAY['CUSTOMER']),
    ('5eed0010-0000-0000-0000-000000000043', 'khach@pavex.test',        'Thu Trang', 'Phan',   'FEMALE', '2000-07-07', ARRAY['CUSTOMER']);

INSERT INTO identity.user_account (id, email, status, profile_status, email_verified, authorization_version,
                                   created_at, updated_at, version)
SELECT id, email, 'ACTIVE', 'COMPLETED', TRUE, 1, NOW(), NOW(), 0 FROM seed_user;

INSERT INTO identity.user_profile (user_id, first_name, last_name, display_name, gender, date_of_birth, avatar_url,
                                   created_at, updated_at)
SELECT id, first_name, last_name, last_name || ' ' || first_name, gender, date_of_birth, NULL, NOW(), NOW() FROM seed_user;

INSERT INTO identity.user_role (user_id, role_id, assigned_at, assigned_by)
SELECT s.id, r.id, NOW(), '00000000-0000-0000-0000-000000000001'
FROM seed_user s CROSS JOIN LATERAL unnest(s.roles) AS assigned(role_code) JOIN identity.role r ON r.code = assigned.role_code;

COMMIT;
