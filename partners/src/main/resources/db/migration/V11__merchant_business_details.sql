-- What a shop is, beyond its name and tax code: the business form, its registration, who
-- represents it, where it is registered, where it sells, what it sells and how much. Optional;
-- the business form, registration and representative are locked once the shop is verified.
ALTER TABLE partners.merchants
    ADD COLUMN business_type           VARCHAR(20),
    ADD COLUMN registration_number     VARCHAR(50),
    ADD COLUMN representative_name     VARCHAR(200),
    ADD COLUMN registered_address      VARCHAR(500),
    ADD COLUMN sales_channel           VARCHAR(500),
    ADD COLUMN product_category        VARCHAR(100),
    ADD COLUMN expected_monthly_orders INTEGER,
    ADD CONSTRAINT ck_merchant_business_type CHECK (business_type IS NULL OR business_type IN ('INDIVIDUAL', 'HOUSEHOLD', 'COMPANY')),
    ADD CONSTRAINT ck_merchant_expected_orders CHECK (expected_monthly_orders IS NULL OR expected_monthly_orders >= 0);
