CREATE SCHEMA IF NOT EXISTS network;

CREATE TABLE network.regions (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_region_status CHECK (status IN ('INACTIVE', 'ACTIVE'))
);

CREATE TABLE network.hubs (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_code VARCHAR(20) NOT NULL,
    parent_hub_id UUID NULL REFERENCES network.hubs(id),
    public_visible BOOLEAN NOT NULL DEFAULT FALSE,
    public_name VARCHAR(200) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_hub_type CHECK (type IN ('REGIONAL_GATEWAY', 'PROVINCIAL', 'LOCAL')),
    CONSTRAINT ck_network_hub_status CHECK (status IN ('INACTIVE', 'ACTIVE')),
    CONSTRAINT ck_network_hub_parent_self CHECK (parent_hub_id IS NULL OR parent_hub_id <> id)
);

CREATE INDEX ix_network_hubs_parent ON network.hubs(parent_hub_id);
CREATE INDEX ix_network_hubs_location ON network.hubs(province_code, ward_code);
CREATE INDEX ix_network_hubs_public ON network.hubs(public_visible, status, province_code);

CREATE TABLE network.service_areas (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    region_id UUID NOT NULL REFERENCES network.regions(id),
    primary_hub_id UUID NOT NULL REFERENCES network.hubs(id),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_service_area_status CHECK (status IN ('INACTIVE', 'ACTIVE'))
);

CREATE INDEX ix_network_service_areas_region ON network.service_areas(region_id);
CREATE INDEX ix_network_service_areas_primary_hub ON network.service_areas(primary_hub_id);

CREATE TABLE network.service_area_coverages (
    id UUID PRIMARY KEY,
    service_area_id UUID NOT NULL REFERENCES network.service_areas(id),
    province_code VARCHAR(20) NOT NULL,
    ward_code VARCHAR(20) NULL,
    tier VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_network_coverage_tier CHECK (tier IN ('STANDARD', 'REMOTE', 'ISLAND'))
);

CREATE INDEX ix_network_coverages_area ON network.service_area_coverages(service_area_id);
CREATE INDEX ix_network_coverages_location ON network.service_area_coverages(province_code, ward_code);
CREATE UNIQUE INDEX ux_network_active_province_coverage
    ON network.service_area_coverages(province_code)
    WHERE ward_code IS NULL AND is_active = TRUE;
CREATE UNIQUE INDEX ux_network_active_ward_coverage
    ON network.service_area_coverages(ward_code)
    WHERE ward_code IS NOT NULL AND is_active = TRUE;

CREATE TABLE network.audit_logs (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_user_id UUID NOT NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_network_audit_entity_type CHECK (entity_type IN ('REGION', 'HUB', 'SERVICE_AREA', 'COVERAGE')),
    CONSTRAINT ck_network_audit_action CHECK (action IN ('CREATED', 'UPDATED', 'ACTIVATED', 'DEACTIVATED'))
);

CREATE INDEX ix_network_audit_entity ON network.audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_network_audit_actor ON network.audit_logs(actor_user_id, occurred_at DESC);

-- Dynamic defaults only. Provinces are intentionally not hard-wired to these
-- regions; operations can create more granular regions without redeploying.
INSERT INTO network.regions (id, code, name, status, created_at, updated_at, version)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'NORTH', 'Miền Bắc', 'ACTIVE', NOW(), NOW(), 0),
    ('30000000-0000-0000-0000-000000000002', 'CENTRAL', 'Miền Trung', 'ACTIVE', NOW(), NOW(), 0),
    ('30000000-0000-0000-0000-000000000003', 'SOUTH', 'Miền Nam', 'ACTIVE', NOW(), NOW(), 0)
ON CONFLICT (code) DO NOTHING;
