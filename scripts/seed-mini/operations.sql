-- Nhân sự vận hành của 2 bưu cục. Mọi dữ liệu vận hành cũ (ca, phân công, hàng đợi) được xoá;
-- ca làm được Operations tự xếp lại sau khi khởi động (lịch 6 ngày làm, 1 ngày nghỉ).
\set ON_ERROR_STOP on
BEGIN;

DO $$ DECLARE t text; BEGIN
  FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'operations' AND tablename NOT LIKE 'flyway%' LOOP
    EXECUTE format('TRUNCATE operations.%I CASCADE', t);
  END LOOP;
END $$;

-- One row per staff member; the first hub is their primary hub, capacity is how many jobs they hold at once.
CREATE TEMP TABLE seed_member (
    member_id UUID, user_id UUID, employee_code TEXT, kind TEXT, capacity INT, hubs UUID[]
) ON COMMIT DROP;

INSERT INTO seed_member VALUES
    ('5eed0020-0000-4000-8000-000000000011', '5eed0010-0000-4000-8000-000000000011', 'PVX000011', 'HUB_MANAGER',        50,  ARRAY['5eed0001-0000-4000-8000-000000000001'::uuid]),
    ('5eed0020-0000-4000-8000-000000000012', '5eed0010-0000-4000-8000-000000000012', 'PVX000012', 'WAREHOUSE_OPERATOR', 100, ARRAY['5eed0001-0000-4000-8000-000000000001'::uuid]),
    ('5eed0020-0000-4000-8000-000000000013', '5eed0010-0000-4000-8000-000000000013', 'PVX000013', 'COURIER',            20,  ARRAY['5eed0001-0000-4000-8000-000000000001'::uuid]),
    ('5eed0020-0000-4000-8000-000000000021', '5eed0010-0000-4000-8000-000000000021', 'PVX000021', 'HUB_MANAGER',        50,  ARRAY['5eed0001-0000-4000-8000-000000000002'::uuid]),
    ('5eed0020-0000-4000-8000-000000000022', '5eed0010-0000-4000-8000-000000000022', 'PVX000022', 'WAREHOUSE_OPERATOR', 100, ARRAY['5eed0001-0000-4000-8000-000000000002'::uuid]),
    ('5eed0020-0000-4000-8000-000000000023', '5eed0010-0000-4000-8000-000000000023', 'PVX000023', 'COURIER',            20,  ARRAY['5eed0001-0000-4000-8000-000000000002'::uuid]),
    -- The driver is based in Hà Nội and also works out of Tân Sơn Hoà, where the trucks arrive.
    ('5eed0020-0000-4000-8000-000000000031', '5eed0010-0000-4000-8000-000000000031', 'PVX000031', 'LINE_HAUL_DRIVER',   100,
     ARRAY['5eed0001-0000-4000-8000-000000000001'::uuid, '5eed0001-0000-4000-8000-000000000002'::uuid]);

INSERT INTO operations.workforce_members (id, user_id, employee_code, kind, status, created_at, updated_at, version)
SELECT member_id, user_id, employee_code, kind, 'ACTIVE', NOW(), NOW(), 0 FROM seed_member;

INSERT INTO operations.hub_memberships (id, workforce_member_id, hub_id, is_primary, is_active, assigned_at,
                                        unassigned_at, updated_at, version)
SELECT md5(m.member_id::text || hub.id::text)::uuid, m.member_id, hub.id, hub.position = 1, TRUE, NOW(), NULL, NOW(), 0
FROM seed_member m CROSS JOIN LATERAL unnest(m.hubs) WITH ORDINALITY AS hub(id, position);

INSERT INTO operations.workforce_availabilities (workforce_member_id, status, max_concurrent_assignments, updated_at, version)
SELECT member_id, 'OFF_DUTY', capacity, NOW(), 0 FROM seed_member;

COMMIT;
