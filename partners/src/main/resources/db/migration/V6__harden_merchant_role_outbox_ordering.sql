-- A merchant role change is a "latest state wins" signal per user.
-- Older pending messages are marked superseded instead of being sent after a
-- newer change, which previously could re-grant MERCHANT after a suspension.
ALTER TABLE partners.merchant_role_outbox
    ADD COLUMN superseded_at TIMESTAMPTZ;

CREATE INDEX ix_merchant_role_outbox_user_occurred
    ON partners.merchant_role_outbox(user_id, occurred_at, id);

CREATE INDEX ix_merchant_role_outbox_dispatchable
    ON partners.merchant_role_outbox(available_at, occurred_at)
    WHERE processed_at IS NULL;
