-- Each merchant belongs to the hub that serves the ward of its default pickup point: that hub's
-- manager approves it and its staff look after it. Null until the merchant has a pickup point
-- (or while Network could not be asked); only head office sees those.
ALTER TABLE partners.merchants ADD COLUMN serving_hub_id UUID NULL;
CREATE INDEX ix_merchants_serving_hub ON partners.merchants(serving_hub_id, created_at DESC);
