-- Zones are now nội tỉnh / nội miền / liên miền. "Same service area" was always inside one
-- province, so existing rules and quotes keep their price under the new name.
ALTER TABLE pricing.rate_rules DROP CONSTRAINT ck_pricing_rate_rule_zone;
UPDATE pricing.rate_rules SET pricing_zone = 'SAME_PROVINCE' WHERE pricing_zone = 'SAME_SERVICE_AREA';
ALTER TABLE pricing.rate_rules ADD CONSTRAINT ck_pricing_rate_rule_zone
    CHECK (pricing_zone IN ('SAME_PROVINCE', 'SAME_REGION', 'CROSS_REGION'));

ALTER TABLE pricing.quote_requests DROP CONSTRAINT ck_pricing_quote_zone;
UPDATE pricing.quote_requests SET pricing_zone = 'SAME_PROVINCE' WHERE pricing_zone = 'SAME_SERVICE_AREA';
ALTER TABLE pricing.quote_requests ADD CONSTRAINT ck_pricing_quote_zone
    CHECK (pricing_zone IN ('SAME_PROVINCE', 'SAME_REGION', 'CROSS_REGION'));
