CREATE TABLE partners.merchant_role_outbox (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES partners.merchants(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    enabled BOOLEAN NOT NULL,
    assigned_by_user_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000)
);

CREATE INDEX ix_merchant_role_outbox_pending
    ON partners.merchant_role_outbox(processed_at, available_at);
CREATE INDEX ix_merchant_role_outbox_merchant_occurred
    ON partners.merchant_role_outbox(merchant_id, occurred_at DESC);
