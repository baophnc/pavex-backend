-- Counter (walk-in) shipments. Staff at a post office weigh a walk-in customer's parcels, quote,
-- take the cash and create the shipment there; later they pay the sender's COD back in cash, or
-- take the shipping fee of a returned shipment the recipient never paid. Only the hub staff who
-- serve the counter hold these; head office reviews the cash through billing.cash.approve.
INSERT INTO identity.permission (id, code, name, description)
VALUES
    ('20000000-0000-0000-0000-000000000063', 'shipments.counter.create', 'Create Counter Shipments', 'Creates shipments for walk-in customers at the counter of an assigned hub and looks up saved counter contacts'),
    ('20000000-0000-0000-0000-000000000064', 'billing.counter.settle', 'Settle Counter Cash', 'Pays COD back to walk-in senders and takes unpaid return fees at the counter of an assigned hub')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

WITH mappings(role_code, permission_code) AS (
    VALUES
    ('HUB_MANAGER', 'shipments.counter.create'),
    ('HUB_MANAGER', 'billing.counter.settle'),
    ('WAREHOUSE_OPERATOR', 'shipments.counter.create'),
    ('WAREHOUSE_OPERATOR', 'billing.counter.settle'),
    ('ADMIN', 'shipments.counter.create'),
    ('ADMIN', 'billing.counter.settle')
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
    WHERE ur.user_id = u.id AND r.code IN ('ADMIN', 'HUB_MANAGER', 'WAREHOUSE_OPERATOR')
);
