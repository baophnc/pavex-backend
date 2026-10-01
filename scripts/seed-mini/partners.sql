-- Cửa hàng test: 1 cửa hàng đã duyệt (điểm lấy hàng ở Phường Ba Đình) và 1 cửa hàng chờ duyệt.
-- Mọi cửa hàng cũ bị xoá; địa chỉ của những tài khoản không còn tồn tại được dọn ở apply.sh.
\set ON_ERROR_STOP on
BEGIN;

TRUNCATE partners.merchants, partners.merchant_role_outbox CASCADE;

INSERT INTO partners.merchants (id, merchant_code, user_id, business_name, contact_name, contact_phone, contact_email,
                                tax_code, status, status_reason_code, status_reason_detail, status_changed_at,
                                status_changed_by_user_id, verified_at, verified_by_user_id, version, created_at, updated_at) VALUES
    ('5eed0030-0000-0000-0000-000000000001', 'PVM000001', '5eed0010-0000-0000-0000-000000000041',
     'Cửa hàng Hoa Sen', 'Bùi Ngọc Mai', '0912000041', 'shop.daduyet@pavex.test', '0109000041',
     'ACTIVE', NULL, NULL, NOW(), '5eed0010-0000-0000-0000-000000000004',
     NOW(), '5eed0010-0000-0000-0000-000000000004', 0, NOW(), NOW()),
    ('5eed0030-0000-0000-0000-000000000002', 'PVM000002', '5eed0010-0000-0000-0000-000000000042',
     'Cửa hàng Mai Vàng', 'Đặng Hữu Phước', '0912000042', 'shop.choduyet@pavex.test', '0309000042',
     'PENDING_VERIFICATION', NULL, NULL, NOW(), NULL, NULL, NULL, 0, NOW(), NOW());

INSERT INTO partners.merchant_pickup_addresses (id, merchant_id, name, contact_name, contact_phone, address_line,
                                                province_code, ward_code, is_default, created_at, updated_at) VALUES
    ('5eed0031-0000-0000-0000-000000000001', '5eed0030-0000-0000-0000-000000000001', 'Kho Ba Đình',
     'Bùi Ngọc Mai', '0912000041', 'Số 25 Liễu Giai', '01', '00004', TRUE, NOW(), NOW());

INSERT INTO partners.merchant_status_history (id, merchant_id, from_status, to_status, reason_code, reason_detail,
                                              changed_by_user_id, changed_at) VALUES
    ('5eed0032-0000-0000-0000-000000000001', '5eed0030-0000-0000-0000-000000000001', 'PENDING_VERIFICATION', 'ACTIVE',
     NULL, 'Duyệt sẵn bởi seed-mini', '5eed0010-0000-0000-0000-000000000004', NOW());

SELECT setval('partners.merchant_code_seq', 2, true);

COMMIT;
