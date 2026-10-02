-- Hub staff count the cash couriers and walk-in senders hand in; the hub manager approves it for
-- their own hub (Billing checks the hub), and the operations manager sees and approves every hub.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code = 'billing.cash.approve'
WHERE r.code = 'HUB_MANAGER'
ON CONFLICT DO NOTHING;

UPDATE identity.user_account u
SET authorization_version = authorization_version + 1, updated_at = NOW(), version = version + 1
WHERE EXISTS (
    SELECT 1 FROM identity.user_role ur JOIN identity.role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id AND r.code = 'HUB_MANAGER'
);
