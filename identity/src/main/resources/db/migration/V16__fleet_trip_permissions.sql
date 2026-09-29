-- Line-haul trip tracking (Fleet service). Drivers report the GPS position of their own trips;
-- hub managers watch trips leaving or arriving at their hubs, and head office (scope.all-hubs)
-- watches every trip. Customers, shops and other hub staff never see positions.
INSERT INTO identity.permission (id, code, name, description)
VALUES
    ('20000000-0000-0000-0000-000000000061', 'fleet.trips.report-own', 'Report Own Trip Location', 'Starts and ends own line-haul trips and sends their GPS positions'),
    ('20000000-0000-0000-0000-000000000062', 'fleet.trips.read-hub', 'Watch Line-haul Trips', 'Watches line-haul trips leaving or arriving at the hubs in scope, with their GPS track')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

WITH mappings(role_code, permission_code) AS (
    VALUES
    ('LINE_HAUL_DRIVER', 'fleet.trips.report-own'),
    ('HUB_MANAGER', 'fleet.trips.read-hub'),
    ('OPERATIONS_MANAGER', 'fleet.trips.read-hub'),
    ('ADMIN', 'fleet.trips.report-own'),
    ('ADMIN', 'fleet.trips.read-hub')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mappings m
JOIN identity.role r ON r.code = m.role_code
JOIN identity.permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

-- Tokens issued before this migration do not carry the new permissions.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.code IN ('ADMIN', 'LINE_HAUL_DRIVER', 'HUB_MANAGER', 'OPERATIONS_MANAGER')
);
