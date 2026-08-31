CREATE TABLE shipments.shipment_cases (
    id UUID PRIMARY KEY,
    case_number VARCHAR(32) NOT NULL UNIQUE,
    case_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    hub_id UUID NOT NULL,
    shipment_id UUID NULL REFERENCES shipments.shipments(id) ON DELETE RESTRICT,
    parcel_id UUID NULL REFERENCES shipments.parcels(id) ON DELETE RESTRICT,
    external_barcode VARCHAR(120),
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    reported_by_user_id UUID NOT NULL,
    assigned_to_user_id UUID,
    resolution VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipment_case_type CHECK (case_type IN ('PROBLEM_PARCEL', 'UNIDENTIFIED_PARCEL')),
    CONSTRAINT ck_shipment_case_status CHECK (status IN ('OPEN', 'ASSIGNED', 'INVESTIGATING', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_shipment_case_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_shipment_case_status_updated
    ON shipments.shipment_cases(status, updated_at DESC);
CREATE INDEX ix_shipment_case_hub_status_updated
    ON shipments.shipment_cases(hub_id, status, updated_at DESC);
CREATE INDEX ix_shipment_case_type_status_updated
    ON shipments.shipment_cases(case_type, status, updated_at DESC);
CREATE INDEX ix_shipment_case_shipment
    ON shipments.shipment_cases(shipment_id)
    WHERE shipment_id IS NOT NULL;
CREATE INDEX ix_shipment_case_parcel
    ON shipments.shipment_cases(parcel_id)
    WHERE parcel_id IS NOT NULL;
CREATE INDEX ix_shipment_case_assignee
    ON shipments.shipment_cases(assigned_to_user_id)
    WHERE assigned_to_user_id IS NOT NULL;
CREATE INDEX ix_shipment_case_external_barcode
    ON shipments.shipment_cases(external_barcode)
    WHERE external_barcode IS NOT NULL;

CREATE TABLE shipments.shipment_case_events (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES shipments.shipment_cases(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    actor_user_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    data_json TEXT
);

CREATE INDEX ix_shipment_case_event_timeline
    ON shipments.shipment_case_events(case_id, occurred_at ASC, id ASC);
