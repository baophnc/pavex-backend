-- Pricing zones are now nội tỉnh / nội miền / liên miền (see pricing V5).
ALTER TABLE shipments.shipments DROP CONSTRAINT ck_shipment_pricing_zone;
UPDATE shipments.shipments SET pricing_zone = 'SAME_PROVINCE' WHERE pricing_zone = 'SAME_SERVICE_AREA';
ALTER TABLE shipments.shipments ADD CONSTRAINT ck_shipment_pricing_zone
    CHECK (pricing_zone IN ('SAME_PROVINCE', 'SAME_REGION', 'CROSS_REGION'));
