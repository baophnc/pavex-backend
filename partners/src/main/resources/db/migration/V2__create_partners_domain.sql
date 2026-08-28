CREATE TABLE partners.administrative_regions (
    id INTEGER PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    code_name VARCHAR(255) NOT NULL,
    code_name_en VARCHAR(255) NOT NULL
);

CREATE TABLE partners.administrative_units (
    id INTEGER PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    full_name_en VARCHAR(255) NOT NULL,
    short_name VARCHAR(255) NOT NULL,
    short_name_en VARCHAR(255) NOT NULL,
    code_name VARCHAR(255) NOT NULL,
    code_name_en VARCHAR(255) NOT NULL
);

CREATE TABLE partners.provinces (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    full_name_en VARCHAR(255) NOT NULL,
    code_name VARCHAR(255) NOT NULL UNIQUE,
    administrative_unit_id INTEGER NOT NULL
        REFERENCES partners.administrative_units(id) ON DELETE RESTRICT
);

CREATE TABLE partners.wards (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    name_en VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    full_name_en VARCHAR(255) NOT NULL,
    code_name VARCHAR(255) NOT NULL,
    province_code VARCHAR(20) NOT NULL
        REFERENCES partners.provinces(code) ON DELETE RESTRICT,
    administrative_unit_id INTEGER NOT NULL
        REFERENCES partners.administrative_units(id) ON DELETE RESTRICT,
    CONSTRAINT uq_wards_province_code_code UNIQUE (province_code, code)
);

CREATE INDEX ix_wards_province_code ON partners.wards(province_code);
CREATE INDEX ix_wards_code_name ON partners.wards(code_name);

CREATE TABLE partners.user_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    label VARCHAR(100) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_code VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_user_address_location
        FOREIGN KEY (province_code, ward_code)
        REFERENCES partners.wards(province_code, code) ON DELETE RESTRICT
);

CREATE INDEX ix_user_addresses_user_id
    ON partners.user_addresses(user_id);
CREATE UNIQUE INDEX ux_user_addresses_user_default
    ON partners.user_addresses(user_id) WHERE is_default = TRUE;

CREATE SEQUENCE partners.merchant_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE partners.merchants (
    id UUID PRIMARY KEY,
    merchant_code VARCHAR(20) NOT NULL UNIQUE,
    user_id UUID NOT NULL UNIQUE,
    business_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    contact_email VARCHAR(320),
    tax_code VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    status_reason_code VARCHAR(50),
    status_reason_detail VARCHAR(500),
    status_changed_at TIMESTAMPTZ NOT NULL,
    status_changed_by_user_id UUID,
    verified_at TIMESTAMPTZ,
    verified_by_user_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_merchant_status CHECK (
        status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'REJECTED')
    )
);

CREATE INDEX ix_merchants_status ON partners.merchants(status);
CREATE INDEX ix_merchants_business_name ON partners.merchants(business_name);
CREATE INDEX ix_merchants_tax_code ON partners.merchants(tax_code);

CREATE TABLE partners.merchant_status_history (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES partners.merchants(id) ON DELETE CASCADE,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    reason_code VARCHAR(50),
    reason_detail VARCHAR(500),
    changed_by_user_id UUID,
    changed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_merchant_status_history_merchant_changed
    ON partners.merchant_status_history(merchant_id, changed_at DESC);

CREATE TABLE partners.merchant_pickup_addresses (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES partners.merchants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(200) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_code VARCHAR(20) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_pickup_address_location
        FOREIGN KEY (province_code, ward_code)
        REFERENCES partners.wards(province_code, code) ON DELETE RESTRICT
);

CREATE INDEX ix_pickup_addresses_merchant_id
    ON partners.merchant_pickup_addresses(merchant_id);
CREATE UNIQUE INDEX ux_pickup_addresses_merchant_default
    ON partners.merchant_pickup_addresses(merchant_id) WHERE is_default = TRUE;
