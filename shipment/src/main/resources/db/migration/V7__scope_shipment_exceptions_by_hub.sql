-- Bind exception workflows to a hub so scoped operators cannot review or
-- mutate another hub's exception merely by knowing a shipment/request UUID.
ALTER TABLE shipments.shipment_exception_requests
    ADD COLUMN IF NOT EXISTS hub_id UUID;

-- Conservative backfill for existing rows. Prefer an explicit hold hub, then
-- an active parcel hub, then the most recent recorded operational event. Rows
-- that cannot be attributed safely remain NULL and are visible only to global
-- operators until explicitly re-created/re-associated.
UPDATE shipments.shipment_exception_requests request
SET hub_id = COALESCE(
    shipment.hold_hub_id,
    (
        SELECT parcel.current_hub_id
        FROM shipments.parcels parcel
        WHERE parcel.shipment_id = shipment.id
          AND parcel.current_hub_id IS NOT NULL
        ORDER BY parcel.updated_at DESC, parcel.id
        LIMIT 1
    ),
    (
        SELECT event.hub_id
        FROM shipments.shipment_events event
        WHERE event.shipment_id = shipment.id
          AND event.hub_id IS NOT NULL
        ORDER BY event.occurred_at DESC, event.id DESC
        LIMIT 1
    )
)
FROM shipments.shipments shipment
WHERE shipment.id = request.shipment_id
  AND request.hub_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_shipment_exception_requests_hub_status
    ON shipments.shipment_exception_requests (hub_id, status, requested_at DESC);
