-- Walk-in customers the counter has served, looked up by phone so staff do not type the same
-- sender or recipient again. One phone can have several addresses; the list is shared by all hubs.
CREATE TABLE partners.counter_contacts (
    id UUID PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    province_code VARCHAR(20) NOT NULL,
    ward_code VARCHAR(20) NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 1,
    last_used_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_counter_contacts_use_count CHECK (use_count > 0)
);
CREATE UNIQUE INDEX ux_counter_contacts_address
    ON partners.counter_contacts(phone, ward_code, lower(address_line));
CREATE INDEX ix_counter_contacts_phone ON partners.counter_contacts(phone, last_used_at DESC);
