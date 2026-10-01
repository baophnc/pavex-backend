-- Mạng lưới nhỏ để test: 2 bưu cục, mỗi bưu cục phục vụ cả tỉnh/thành của nó, nối với nhau bằng
-- 2 tuyến trục (mỗi chiều 2 chuyến/ngày). Mọi id cố định nên chạy lại cho ra đúng dữ liệu cũ.
\set ON_ERROR_STOP on
BEGIN;

TRUNCATE network.lane_capacity_reservations, network.route_template_legs, network.route_templates,
    network.lane_schedules, network.hub_lanes, network.service_area_coverages, network.service_areas,
    network.hubs, network.audit_logs CASCADE;

INSERT INTO network.regions (id, code, name, status, created_at, updated_at, version) VALUES
    ('30000000-0000-0000-0000-000000000001', 'NORTH', 'Miền Bắc', 'ACTIVE', NOW(), NOW(), 0),
    ('30000000-0000-0000-0000-000000000003', 'SOUTH', 'Miền Nam', 'ACTIVE', NOW(), NOW(), 0)
ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE';

-- Bưu cục Ba Đình (Hà Nội, phường 00004) và Bưu cục Tân Sơn Hoà (TP.HCM, phường 26977).
INSERT INTO network.hubs (id, code, name, type, status, address_line, province_code, ward_code,
                          parent_hub_id, public_visible, public_name, created_at, updated_at, version) VALUES
    ('5eed0001-0000-0000-0000-000000000001', 'HN-BD', 'Bưu cục Ba Đình', 'LOCAL', 'ACTIVE',
     'Số 1 Đội Cấn, Phường Ba Đình, Hà Nội', '01', '00004', NULL, TRUE, 'Bưu cục Ba Đình', NOW(), NOW(), 0),
    ('5eed0001-0000-0000-0000-000000000002', 'HCM-TSH', 'Bưu cục Tân Sơn Hoà', 'LOCAL', 'ACTIVE',
     'Số 10 Cộng Hoà, Phường Tân Sơn Hoà, TP. Hồ Chí Minh', '79', '26977', NULL, TRUE, 'Bưu cục Tân Sơn Hoà', NOW(), NOW(), 0);

-- Mỗi bưu cục phục vụ toàn tỉnh (ward_code NULL): người nhận ở phường nào của 2 thành phố cũng giao được.
INSERT INTO network.service_areas (id, code, name, region_id, primary_hub_id, status, created_at, updated_at, version) VALUES
    ('5eed0002-0000-0000-0000-000000000001', 'SA-HN', 'Hà Nội', '30000000-0000-0000-0000-000000000001',
     '5eed0001-0000-0000-0000-000000000001', 'ACTIVE', NOW(), NOW(), 0),
    ('5eed0002-0000-0000-0000-000000000002', 'SA-HCM', 'TP. Hồ Chí Minh', '30000000-0000-0000-0000-000000000003',
     '5eed0001-0000-0000-0000-000000000002', 'ACTIVE', NOW(), NOW(), 0);

INSERT INTO network.service_area_coverages (id, service_area_id, province_code, ward_code, tier, is_active, created_at, updated_at) VALUES
    ('5eed0003-0000-0000-0000-000000000001', '5eed0002-0000-0000-0000-000000000001', '01', NULL, 'STANDARD', TRUE, NOW(), NOW()),
    ('5eed0003-0000-0000-0000-000000000002', '5eed0002-0000-0000-0000-000000000002', '79', NULL, 'STANDARD', TRUE, NOW(), NOW());

-- Tuyến trục đường bộ khoảng 30 giờ, mỗi chuyến chở tối đa 2 tấn.
INSERT INTO network.hub_lanes (id, code, from_hub_id, to_hub_id, transport_mode, estimated_transit_minutes,
                               distance_km, max_shipment_weight_kg, status, created_at, updated_at, version) VALUES
    ('5eed0004-0000-0000-0000-000000000001', 'HN-BD-HCM-TSH', '5eed0001-0000-0000-0000-000000000001',
     '5eed0001-0000-0000-0000-000000000002', 'ROAD', 1800, 1700, 100, 'ACTIVE', NOW(), NOW(), 0),
    ('5eed0004-0000-0000-0000-000000000002', 'HCM-TSH-HN-BD', '5eed0001-0000-0000-0000-000000000002',
     '5eed0001-0000-0000-0000-000000000001', 'ROAD', 1800, 1700, 100, 'ACTIVE', NOW(), NOW(), 0);

INSERT INTO network.lane_schedules (id, lane_id, departure_time, monday, tuesday, wednesday, thursday, friday,
                                    saturday, sunday, effective_from, effective_to, status, created_at, updated_at,
                                    version, capacity_kg) VALUES
    ('5eed0005-0000-0000-0000-000000000001', '5eed0004-0000-0000-0000-000000000001', '08:00', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
     CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW(), 0, 2000),
    ('5eed0005-0000-0000-0000-000000000002', '5eed0004-0000-0000-0000-000000000001', '20:00', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
     CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW(), 0, 2000),
    ('5eed0005-0000-0000-0000-000000000003', '5eed0004-0000-0000-0000-000000000002', '08:00', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
     CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW(), 0, 2000),
    ('5eed0005-0000-0000-0000-000000000004', '5eed0004-0000-0000-0000-000000000002', '20:00', TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE,
     CURRENT_DATE - 1, NULL, 'ACTIVE', NOW(), NOW(), 0, 2000);

COMMIT;
