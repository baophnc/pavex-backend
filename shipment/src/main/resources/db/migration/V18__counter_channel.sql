-- Walk-in customers hand their parcels in at a hub counter, where staff book the shipment. Such a
-- shipment has no merchant: the channel says how it was booked, and only shop shipments must name
-- their merchant. The parcels start at the origin hub, and when the sender pays the shipping fee
-- the cash taken at the counter is kept here. Existing shipments are all shop shipments.
ALTER TABLE shipments.shipments
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'MERCHANT_WEB',
    ADD COLUMN counter_fee_paid_amount BIGINT NULL,
    ADD COLUMN counter_fee_paid_at TIMESTAMPTZ NULL,
    ALTER COLUMN merchant_id DROP NOT NULL;

ALTER TABLE shipments.shipments
    ADD CONSTRAINT ck_shipment_channel CHECK (channel IN ('MERCHANT_WEB', 'COUNTER')),
    ADD CONSTRAINT ck_shipment_channel_merchant CHECK (
        (channel = 'MERCHANT_WEB' AND merchant_id IS NOT NULL)
        OR (channel = 'COUNTER' AND merchant_id IS NULL)
    ),
    ADD CONSTRAINT ck_shipment_counter_fee CHECK (
        counter_fee_paid_amount IS NULL
        OR (channel = 'COUNTER' AND counter_fee_paid_amount >= 0 AND counter_fee_paid_at IS NOT NULL)
    );

-- Counter shipments are looked up per hub (the hub's counter history).
CREATE INDEX ix_shipment_counter_origin_hub
    ON shipments.shipments(origin_hub_id, created_at DESC)
    WHERE channel = 'COUNTER';
