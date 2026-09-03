-- Patch 4: capabilities are now separated from data scope in shipment-service.
-- HUB_MANAGER can receive manage/read capabilities because shipment-service
-- resolves the actor's active workforce hub memberships before every scoped
-- read or mutation.
WITH mappings(role_code, permission_code) AS (
    VALUES
        ('HUB_MANAGER', 'shipments.exceptions.manage'),
        ('HUB_MANAGER', 'shipments.weight.adjust'),
        ('HUB_MANAGER', 'shipments.cases.manage'),
        ('HUB_MANAGER', 'shipments.control-tower.read')
)
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM mappings m
JOIN identity.role r ON r.code = m.role_code
JOIN identity.permission p ON p.code = m.permission_code
ON CONFLICT DO NOTHING;

-- Merchant shipment reads now use the explicit own-data capability. The query
-- layer already predicates by created_by_user_id, so the generic transitional
-- permission is no longer needed for the MERCHANT system role.
DELETE FROM identity.role_permission rp
USING identity.role r, identity.permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'MERCHANT'
  AND p.code = 'shipments.read';

UPDATE identity.user_account u
SET authorization_version = authorization_version + 1,
    updated_at = NOW(),
    version = version + 1
WHERE EXISTS (
    SELECT 1
    FROM identity.user_role ur
    JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND r.code IN ('HUB_MANAGER', 'MERCHANT')
);
