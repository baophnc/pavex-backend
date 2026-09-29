-- The sender of a shipment is one of the merchant's saved pickup addresses (Partners). The address
-- itself is still snapshotted on the shipment; this keeps which pickup point it came from, so
-- pickups can be grouped by pickup point. Older shipments keep NULL.
ALTER TABLE shipments.shipments
    ADD COLUMN pickup_address_id UUID NULL;

CREATE INDEX ix_shipment_pickup_address
    ON shipments.shipments(pickup_address_id, created_at DESC)
    WHERE pickup_address_id IS NOT NULL;
