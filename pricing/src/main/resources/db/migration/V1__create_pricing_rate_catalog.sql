CREATE SCHEMA IF NOT EXISTS pricing;

CREATE TABLE pricing.rate_plans (
    id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    revision INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL,
    effective_from TIMESTAMPTZ NULL,
    effective_to TIMESTAMPTZ NULL,
    quote_ttl_minutes INTEGER NOT NULL,
    volumetric_divisor NUMERIC(12, 2) NOT NULL,
    remote_endpoint_surcharge BIGINT NOT NULL,
    island_transport_rate NUMERIC(10, 6) NOT NULL,
    cod_free_threshold BIGINT NOT NULL,
    cod_rate NUMERIC(10, 6) NOT NULL,
    cod_minimum_fee BIGINT NOT NULL,
    maximum_cod BIGINT NOT NULL,
    insurance_free_threshold BIGINT NOT NULL,
    insurance_rate NUMERIC(10, 6) NOT NULL,
    maximum_declared_value BIGINT NOT NULL,
    max_parcel_weight_kg NUMERIC(10, 2) NOT NULL,
    max_shipment_chargeable_weight_kg NUMERIC(10, 2) NOT NULL,
    max_dimension_cm NUMERIC(10, 2) NOT NULL,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    activated_by UUID NULL,
    activated_at TIMESTAMPTZ NULL,
    retired_by UUID NULL,
    retired_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_pricing_rate_plan_code_revision UNIQUE (code, revision),
    CONSTRAINT ck_pricing_rate_plan_revision CHECK (revision > 0),
    CONSTRAINT ck_pricing_rate_plan_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_pricing_rate_plan_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_pricing_rate_plan_window CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    CONSTRAINT ck_pricing_rate_plan_ttl CHECK (quote_ttl_minutes BETWEEN 1 AND 240),
    CONSTRAINT ck_pricing_rate_plan_volumetric_divisor CHECK (volumetric_divisor > 0),
    CONSTRAINT ck_pricing_rate_plan_remote_surcharge CHECK (remote_endpoint_surcharge >= 0),
    CONSTRAINT ck_pricing_rate_plan_island_rate CHECK (island_transport_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_pricing_rate_plan_cod CHECK (
        cod_free_threshold >= 0 AND cod_rate BETWEEN 0 AND 1 AND cod_minimum_fee >= 0 AND maximum_cod >= 0
    ),
    CONSTRAINT ck_pricing_rate_plan_insurance CHECK (
        insurance_free_threshold >= 0 AND insurance_rate BETWEEN 0 AND 1 AND maximum_declared_value >= 0
    ),
    CONSTRAINT ck_pricing_rate_plan_weights CHECK (
        max_parcel_weight_kg > 0
        AND max_shipment_chargeable_weight_kg >= max_parcel_weight_kg
        AND max_dimension_cm > 0
    )
);

CREATE INDEX ix_pricing_rate_plans_status_window
    ON pricing.rate_plans(status, effective_from, effective_to);
CREATE INDEX ix_pricing_rate_plans_updated
    ON pricing.rate_plans(updated_at DESC);

CREATE TABLE pricing.rate_rules (
    id UUID PRIMARY KEY,
    rate_plan_id UUID NOT NULL REFERENCES pricing.rate_plans(id) ON DELETE CASCADE,
    service_level VARCHAR(20) NOT NULL,
    pricing_zone VARCHAR(30) NOT NULL,
    base_fee BIGINT NOT NULL,
    included_weight_kg NUMERIC(10, 2) NOT NULL,
    additional_weight_step_kg NUMERIC(10, 2) NOT NULL,
    additional_weight_step_fee BIGINT NOT NULL,
    estimated_min_days INTEGER NOT NULL,
    estimated_max_days INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_pricing_rate_rule UNIQUE (rate_plan_id, service_level, pricing_zone),
    CONSTRAINT ck_pricing_rate_rule_service_level CHECK (service_level IN ('STANDARD', 'EXPRESS')),
    CONSTRAINT ck_pricing_rate_rule_zone CHECK (pricing_zone IN ('SAME_SERVICE_AREA', 'SAME_REGION', 'CROSS_REGION')),
    CONSTRAINT ck_pricing_rate_rule_money CHECK (base_fee >= 0 AND additional_weight_step_fee >= 0),
    CONSTRAINT ck_pricing_rate_rule_weights CHECK (included_weight_kg >= 0 AND additional_weight_step_kg > 0),
    CONSTRAINT ck_pricing_rate_rule_eta CHECK (
        estimated_min_days >= 0 AND estimated_max_days >= estimated_min_days
    )
);

CREATE INDEX ix_pricing_rate_rules_plan ON pricing.rate_rules(rate_plan_id);
