-- Hub managers also work shifts, scan parcels and take hub jobs themselves, so they need
-- the same "my work" permissions as their staff (my shifts, my availability, my jobs).
WITH mappings(role_code, permission_code) AS (
    VALUES
    ('HUB_MANAGER', 'assignments.read-own'),
    ('HUB_MANAGER', 'assignments.accept-own'),
    ('HUB_MANAGER', 'assignments.execute-own')
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
    SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.code = 'HUB_MANAGER'
);
