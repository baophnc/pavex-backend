-- Network releases shipment capacity reservations that are never confirmed.
-- Shipment confirms right after persisting and retries from a background job.
ALTER TABLE shipments.shipments
    ADD COLUMN network_confirmed_at TIMESTAMPTZ NULL,
    ADD COLUMN network_confirmation_error VARCHAR(200) NULL;

-- Rows created before this migration were reserved without a deadline in Network,
-- so they never need confirmation.
UPDATE shipments.shipments
SET network_confirmed_at = created_at
WHERE network_confirmed_at IS NULL;

CREATE INDEX ix_shipment_network_confirmation_pending
    ON shipments.shipments(created_at)
    WHERE network_confirmed_at IS NULL
      AND network_confirmation_error IS NULL
      AND capacity_reservation_batch_id IS NOT NULL
      AND cancelled_at IS NULL;
