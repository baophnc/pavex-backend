-- A cancellation completed after pickup is charged to the merchant (the recipient never paid).
-- Shipments closed before this column existed were already settled as uncharged, so they keep FALSE.
ALTER TABLE shipments.shipments
    ADD COLUMN cancelled_after_pickup BOOLEAN NOT NULL DEFAULT FALSE;
