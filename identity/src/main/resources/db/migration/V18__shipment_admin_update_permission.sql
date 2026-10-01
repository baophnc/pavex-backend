-- Staff correct a shipment's recipient contact details or note, also after pickup (hub managers
-- only for shipments that touch their hubs). Each change is written on the shipment timeline.
INSERT INTO identity.permission (id, code, name, description)
VALUES ('20000000-0000-0000-0000-000000000066', 'shipments.admin.update', 'Update Shipments',
        'Corrects the recipient contact details and the note of a shipment, with a reason')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description;

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'shipments.admin.update'
WHERE r.code IN ('ADMIN', 'OPERATIONS_MANAGER', 'COMMERCIAL_MANAGER', 'HUB_MANAGER')
ON CONFLICT DO NOTHING;

-- Tokens issued before this migration do not carry the new permission.
UPDATE identity.user_account u
SET authorization_version = authorization_version + 1, updated_at = NOW(), version = version + 1
WHERE EXISTS (SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
              WHERE ur.user_id = u.id AND r.code IN ('ADMIN', 'OPERATIONS_MANAGER', 'COMMERCIAL_MANAGER', 'HUB_MANAGER'));
