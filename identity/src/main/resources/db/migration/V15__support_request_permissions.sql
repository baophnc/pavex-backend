-- Customer care works the support inbox (contact, support, claim and career requests) in the
-- management portal. ADMIN keeps the full catalog; COMMERCIAL_MANAGER ("Kinh doanh & CSKH") owns
-- the inbox.
INSERT INTO identity.permission (id, code, name, description)
VALUES
    ('20000000-0000-0000-0000-000000000059', 'support.requests.read', 'Read Support Requests', 'Views customer contact, support, claim and career requests'),
    ('20000000-0000-0000-0000-000000000060', 'support.requests.manage', 'Manage Support Requests', 'Takes, resolves and closes customer requests')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

WITH mappings(role_code, permission_code) AS (
    VALUES
    ('COMMERCIAL_MANAGER', 'support.requests.read'),
    ('COMMERCIAL_MANAGER', 'support.requests.manage')
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
  AND p.code IN ('support.requests.read', 'support.requests.manage')
ON CONFLICT DO NOTHING;

-- Tokens issued before this migration do not carry the new permissions.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.code IN ('ADMIN', 'COMMERCIAL_MANAGER')
);
