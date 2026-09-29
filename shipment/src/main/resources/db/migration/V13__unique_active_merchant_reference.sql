-- A merchant reference identifies one live order per merchant, so re-submitting the same order
-- cannot create a second shipment. Cancelled shipments release their reference for reuse.
CREATE UNIQUE INDEX ux_shipment_active_merchant_reference
    ON shipments.shipments(merchant_id, merchant_reference)
    WHERE merchant_reference IS NOT NULL AND lifecycle_status <> 'CANCELLED';
