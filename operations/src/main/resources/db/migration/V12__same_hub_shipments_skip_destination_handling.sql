-- An order sent and delivered by the same post office has no truck and no separate arrival: the
-- origin handling is its only sorting step and finishing it releases the delivery. Plans made before
-- this still carry a destination handling at that hub which no scan can ever reach, so the delivery
-- behind it stayed blocked. Cancel that step and, where the origin handling is already done, release
-- the delivery so dispatch finds a courier. The assignment triggers record both changes as events.
CREATE TEMPORARY TABLE same_hub_destination_handling ON COMMIT DROP AS
SELECT destination.id, destination.shipment_id
FROM operations.operational_assignments destination
JOIN operations.operational_assignments origin
    ON origin.shipment_id = destination.shipment_id
    AND origin.type = 'ORIGIN_HUB_HANDLING'
    AND origin.hub_id = destination.hub_id
WHERE destination.type = 'DESTINATION_HUB_HANDLING'
  AND destination.status NOT IN ('COMPLETED', 'CANCELLED')
  AND NOT EXISTS (
      SELECT 1
      FROM operations.operational_assignments leg
      WHERE leg.shipment_id = destination.shipment_id
        AND leg.type IN ('LINE_HAUL', 'TRANSIT_HUB_HANDLING')
  );

UPDATE operations.operational_assignments assignment
SET status = 'CANCELLED',
    resolution_note = 'Đơn đi và đến cùng một bưu cục: bước xử lý tại bưu cục gửi đã bao gồm bước này.',
    cancelled_at = now(),
    updated_at = now(),
    version = assignment.version + 1
FROM same_hub_destination_handling stuck
WHERE assignment.id = stuck.id;

UPDATE operations.operational_assignments delivery
SET status = 'READY',
    updated_at = now(),
    version = delivery.version + 1
FROM same_hub_destination_handling stuck
WHERE delivery.shipment_id = stuck.shipment_id
  AND delivery.type = 'LAST_MILE_DELIVERY'
  AND delivery.status = 'BLOCKED'
  AND EXISTS (
      SELECT 1
      FROM operations.operational_assignments origin
      WHERE origin.shipment_id = stuck.shipment_id
        AND origin.type = 'ORIGIN_HUB_HANDLING'
        AND origin.status = 'COMPLETED'
  );
