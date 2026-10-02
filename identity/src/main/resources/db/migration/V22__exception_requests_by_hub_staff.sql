-- Hub staff spot the problem (recipient refuses, wants a new address, the shop cancels after
-- pickup) and ask for a return, a forward or a cancellation; the hub manager approves it. A request
-- can never be approved by the person who made it.
INSERT INTO identity.permission (id, code, name, description)
VALUES ('20000000-0000-0000-0000-000000000067', 'shipments.exceptions.request', 'Request Shipment Exceptions',
        'Asks for a return, a forward to a new address or a cancellation; someone else approves it')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'shipments.exceptions.request'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER', 'HUB_MANAGER', 'WAREHOUSE_OPERATOR')
ON CONFLICT DO NOTHING;

UPDATE identity.user_account u
SET authorization_version = authorization_version + 1, updated_at = NOW(), version = version + 1
WHERE EXISTS (SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.code IN ('ADMIN', 'OPERATIONS_MANAGER', 'HUB_MANAGER', 'WAREHOUSE_OPERATOR'));
