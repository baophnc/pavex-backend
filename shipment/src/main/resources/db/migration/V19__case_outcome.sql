-- What a case concluded, so the shipment follows: keep delivering, return to the sender, or stop
-- because the parcel was lost or damaged.
ALTER TABLE shipments.shipment_cases ADD COLUMN outcome VARCHAR(30);
ALTER TABLE shipments.shipment_cases ADD CONSTRAINT ck_shipment_case_outcome
    CHECK (outcome IS NULL OR outcome IN ('CONTINUE', 'RETURN_TO_SENDER', 'LOST', 'DAMAGED', 'OTHER'));
