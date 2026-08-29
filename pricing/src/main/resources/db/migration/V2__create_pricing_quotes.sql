CREATE TABLE pricing.quote_requests (
    id UUID PRIMARY KEY,
    rate_plan_id UUID NOT NULL REFERENCES pricing.rate_plans(id) ON DELETE RESTRICT,
    rate_code VARCHAR(60) NOT NULL,
    rate_revision INTEGER NOT NULL,
    rate_version VARCHAR(80) NOT NULL,
    pricing_zone VARCHAR(30) NOT NULL,
    origin_province_code VARCHAR(20) NOT NULL,
    origin_ward_code VARCHAR(20) NOT NULL,
    origin_service_area_id UUID NOT NULL,
    origin_region_id UUID NOT NULL,
    origin_primary_hub_id UUID NOT NULL,
    origin_coverage_tier VARCHAR(20) NOT NULL,
    destination_province_code VARCHAR(20) NOT NULL,
    destination_ward_code VARCHAR(20) NOT NULL,
    destination_service_area_id UUID NOT NULL,
    destination_region_id UUID NOT NULL,
    destination_primary_hub_id UUID NOT NULL,
    destination_coverage_tier VARCHAR(20) NOT NULL,
    actual_weight_kg NUMERIC(12, 2) NOT NULL,
    volumetric_weight_kg NUMERIC(12, 2) NOT NULL,
    chargeable_weight_kg NUMERIC(12, 2) NOT NULL,
    parcel_count INTEGER NOT NULL,
    cod_amount BIGINT NOT NULL,
    declared_value BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pricing_quote_zone CHECK (pricing_zone IN ('SAME_SERVICE_AREA', 'SAME_REGION', 'CROSS_REGION')),
    CONSTRAINT ck_pricing_quote_origin_tier CHECK (origin_coverage_tier IN ('STANDARD', 'REMOTE', 'ISLAND')),
    CONSTRAINT ck_pricing_quote_destination_tier CHECK (destination_coverage_tier IN ('STANDARD', 'REMOTE', 'ISLAND')),
    CONSTRAINT ck_pricing_quote_weights CHECK (
        actual_weight_kg > 0 AND volumetric_weight_kg >= 0 AND chargeable_weight_kg > 0
    ),
    CONSTRAINT ck_pricing_quote_parcel_count CHECK (parcel_count BETWEEN 1 AND 20),
    CONSTRAINT ck_pricing_quote_money CHECK (cod_amount >= 0 AND declared_value >= 0),
    CONSTRAINT ck_pricing_quote_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_pricing_quote_requests_expires_at
    ON pricing.quote_requests(expires_at);
CREATE INDEX ix_pricing_quote_requests_rate_plan
    ON pricing.quote_requests(rate_plan_id, created_at DESC);

CREATE TABLE pricing.quote_parcels (
    id UUID PRIMARY KEY,
    quote_request_id UUID NOT NULL REFERENCES pricing.quote_requests(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    actual_weight_kg NUMERIC(10, 2) NOT NULL,
    volumetric_weight_kg NUMERIC(10, 2) NOT NULL,
    chargeable_weight_kg NUMERIC(10, 2) NOT NULL,
    length_cm NUMERIC(10, 2) NOT NULL,
    width_cm NUMERIC(10, 2) NOT NULL,
    height_cm NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ux_pricing_quote_parcel_sequence UNIQUE (quote_request_id, sequence),
    CONSTRAINT ck_pricing_quote_parcel_sequence CHECK (sequence BETWEEN 1 AND 20),
    CONSTRAINT ck_pricing_quote_parcel_weights CHECK (
        actual_weight_kg > 0 AND volumetric_weight_kg >= 0 AND chargeable_weight_kg > 0
    ),
    CONSTRAINT ck_pricing_quote_parcel_dimensions CHECK (length_cm > 0 AND width_cm > 0 AND height_cm > 0)
);

CREATE INDEX ix_pricing_quote_parcels_request
    ON pricing.quote_parcels(quote_request_id, sequence);

CREATE TABLE pricing.quote_options (
    id UUID PRIMARY KEY,
    quote_request_id UUID NOT NULL REFERENCES pricing.quote_requests(id) ON DELETE CASCADE,
    service_level VARCHAR(20) NOT NULL,
    base_fee BIGINT NOT NULL,
    additional_weight_fee BIGINT NOT NULL,
    area_surcharge BIGINT NOT NULL,
    cod_fee BIGINT NOT NULL,
    insurance_fee BIGINT NOT NULL,
    total BIGINT NOT NULL,
    estimated_min_days INTEGER NOT NULL,
    estimated_max_days INTEGER NOT NULL,
    CONSTRAINT ux_pricing_quote_option_service UNIQUE (quote_request_id, service_level),
    CONSTRAINT ck_pricing_quote_option_service CHECK (service_level IN ('STANDARD', 'EXPRESS')),
    CONSTRAINT ck_pricing_quote_option_money CHECK (
        base_fee >= 0 AND additional_weight_fee >= 0 AND area_surcharge >= 0
        AND cod_fee >= 0 AND insurance_fee >= 0 AND total >= 0
    ),
    CONSTRAINT ck_pricing_quote_option_eta CHECK (
        estimated_min_days >= 0 AND estimated_max_days >= estimated_min_days
    )
);

CREATE INDEX ix_pricing_quote_options_request
    ON pricing.quote_options(quote_request_id);
