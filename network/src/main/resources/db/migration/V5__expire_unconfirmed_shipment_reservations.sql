-- Shipment reservations are made before the shipment row exists. If Shipment
-- crashes (or times out) between reserve and persist, the capacity would stay
-- RESERVED forever. Shipment now confirms a reservation after persisting it;
-- reservations that carry a deadline and are never confirmed are released.
--
-- Admin/manual reservations and all rows created before this migration have no
-- deadline and are never expired automatically.
ALTER TABLE network.lane_capacity_reservations
    ADD COLUMN confirmation_deadline_at TIMESTAMPTZ NULL,
    ADD COLUMN confirmed_at TIMESTAMPTZ NULL;

CREATE INDEX ix_network_capacity_unconfirmed_deadline
    ON network.lane_capacity_reservations(confirmation_deadline_at)
    WHERE status = 'RESERVED'
      AND confirmed_at IS NULL
      AND confirmation_deadline_at IS NOT NULL;
