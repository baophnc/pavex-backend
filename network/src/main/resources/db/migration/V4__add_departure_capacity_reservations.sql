ALTER TABLE network.lane_schedules
    ADD COLUMN capacity_kg NUMERIC(14, 3) NOT NULL DEFAULT 1000.000;

ALTER TABLE network.lane_schedules
    ADD CONSTRAINT ck_network_schedule_capacity CHECK (capacity_kg > 0);

CREATE TABLE network.lane_capacity_reservations (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL,
    shipment_id UUID NOT NULL,
    route_revision INTEGER NOT NULL,
    lane_id UUID NOT NULL REFERENCES network.hub_lanes(id),
    schedule_id UUID NOT NULL REFERENCES network.lane_schedules(id),
    leg_sequence INTEGER NOT NULL,
    service_date DATE NOT NULL,
    departure_at TIMESTAMPTZ NOT NULL,
    arrival_at TIMESTAMPTZ NOT NULL,
    reserved_weight_kg NUMERIC(14, 3) NOT NULL,
    departure_capacity_kg NUMERIC(14, 3) NOT NULL,
    route_source VARCHAR(30) NOT NULL,
    route_template_id UUID NULL REFERENCES network.route_templates(id) ON DELETE SET NULL,
    route_template_code VARCHAR(80) NULL,
    route_template_revision INTEGER NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_capacity_route_revision CHECK (route_revision > 0),
    CONSTRAINT ck_network_capacity_leg_sequence CHECK (leg_sequence > 0),
    CONSTRAINT ck_network_capacity_window CHECK (arrival_at > departure_at),
    CONSTRAINT ck_network_capacity_weight CHECK (reserved_weight_kg > 0),
    CONSTRAINT ck_network_capacity_snapshot CHECK (departure_capacity_kg > 0),
    CONSTRAINT ck_network_capacity_route_source CHECK (route_source IN ('ROUTE_TEMPLATE', 'GRAPH')),
    CONSTRAINT ck_network_capacity_status CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
    CONSTRAINT uq_network_capacity_shipment_revision_leg UNIQUE (shipment_id, route_revision, leg_sequence)
);

CREATE INDEX ix_network_capacity_batch
    ON network.lane_capacity_reservations(batch_id, leg_sequence);
CREATE INDEX ix_network_capacity_departure
    ON network.lane_capacity_reservations(schedule_id, service_date, status);
CREATE INDEX ix_network_capacity_lane_date
    ON network.lane_capacity_reservations(lane_id, service_date, status);
CREATE INDEX ix_network_capacity_shipment
    ON network.lane_capacity_reservations(shipment_id, route_revision);

ALTER TABLE network.audit_logs
    DROP CONSTRAINT ck_network_audit_entity_type;

ALTER TABLE network.audit_logs
    ADD CONSTRAINT ck_network_audit_entity_type
    CHECK (entity_type IN (
        'REGION', 'HUB', 'SERVICE_AREA', 'COVERAGE',
        'LANE', 'LANE_SCHEDULE', 'ROUTE_TEMPLATE', 'CAPACITY_RESERVATION'
    ));

ALTER TABLE network.audit_logs
    DROP CONSTRAINT ck_network_audit_action;

ALTER TABLE network.audit_logs
    ADD CONSTRAINT ck_network_audit_action
    CHECK (action IN (
        'CREATED', 'UPDATED', 'ACTIVATED', 'DEACTIVATED', 'DELETED',
        'RESERVED', 'CONSUMED', 'RELEASED'
    ));
