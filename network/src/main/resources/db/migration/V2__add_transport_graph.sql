CREATE TABLE network.hub_lanes (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    from_hub_id UUID NOT NULL REFERENCES network.hubs(id),
    to_hub_id UUID NOT NULL REFERENCES network.hubs(id),
    transport_mode VARCHAR(20) NOT NULL,
    estimated_transit_minutes INTEGER NOT NULL,
    distance_km NUMERIC(10, 2) NULL,
    max_shipment_weight_kg NUMERIC(12, 3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_lane_different_hubs CHECK (from_hub_id <> to_hub_id),
    CONSTRAINT ck_network_lane_transport_mode CHECK (transport_mode IN ('ROAD', 'AIR', 'RAIL', 'SEA')),
    CONSTRAINT ck_network_lane_transit CHECK (estimated_transit_minutes > 0),
    CONSTRAINT ck_network_lane_distance CHECK (distance_km IS NULL OR distance_km > 0),
    CONSTRAINT ck_network_lane_weight CHECK (max_shipment_weight_kg > 0),
    CONSTRAINT ck_network_lane_status CHECK (status IN ('INACTIVE', 'ACTIVE'))
);

CREATE INDEX ix_network_lanes_from_hub ON network.hub_lanes(from_hub_id, status);
CREATE INDEX ix_network_lanes_to_hub ON network.hub_lanes(to_hub_id, status);
CREATE INDEX ix_network_lanes_mode ON network.hub_lanes(transport_mode, status);

CREATE TABLE network.lane_schedules (
    id UUID PRIMARY KEY,
    lane_id UUID NOT NULL REFERENCES network.hub_lanes(id),
    departure_time TIME NOT NULL,
    monday BOOLEAN NOT NULL DEFAULT FALSE,
    tuesday BOOLEAN NOT NULL DEFAULT FALSE,
    wednesday BOOLEAN NOT NULL DEFAULT FALSE,
    thursday BOOLEAN NOT NULL DEFAULT FALSE,
    friday BOOLEAN NOT NULL DEFAULT FALSE,
    saturday BOOLEAN NOT NULL DEFAULT FALSE,
    sunday BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_schedule_days CHECK (
        monday OR tuesday OR wednesday OR thursday OR friday OR saturday OR sunday
    ),
    CONSTRAINT ck_network_schedule_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_network_schedule_status CHECK (status IN ('INACTIVE', 'ACTIVE'))
);

CREATE INDEX ix_network_lane_schedules_lane ON network.lane_schedules(lane_id, status, departure_time);
CREATE INDEX ix_network_lane_schedules_effective ON network.lane_schedules(status, effective_from, effective_to);

ALTER TABLE network.audit_logs
    DROP CONSTRAINT ck_network_audit_entity_type;

ALTER TABLE network.audit_logs
    ADD CONSTRAINT ck_network_audit_entity_type
    CHECK (entity_type IN ('REGION', 'HUB', 'SERVICE_AREA', 'COVERAGE', 'LANE', 'LANE_SCHEDULE'));
