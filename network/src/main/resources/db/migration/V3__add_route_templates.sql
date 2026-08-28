CREATE TABLE network.route_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    origin_hub_id UUID NOT NULL REFERENCES network.hubs(id),
    destination_hub_id UUID NOT NULL REFERENCES network.hubs(id),
    priority INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_network_route_template_different_hubs CHECK (origin_hub_id <> destination_hub_id),
    CONSTRAINT ck_network_route_template_priority CHECK (priority BETWEEN 1 AND 10000),
    CONSTRAINT ck_network_route_template_status CHECK (status IN ('INACTIVE', 'ACTIVE')),
    CONSTRAINT ck_network_route_template_revision CHECK (revision > 0)
);

CREATE INDEX ix_network_route_templates_journey
    ON network.route_templates(origin_hub_id, destination_hub_id, status, priority);

CREATE TABLE network.route_template_legs (
    id UUID PRIMARY KEY,
    route_template_id UUID NOT NULL REFERENCES network.route_templates(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    lane_id UUID NOT NULL REFERENCES network.hub_lanes(id),
    CONSTRAINT ck_network_route_template_leg_sequence CHECK (sequence > 0),
    CONSTRAINT uq_network_route_template_leg_sequence UNIQUE (route_template_id, sequence),
    CONSTRAINT uq_network_route_template_leg_lane UNIQUE (route_template_id, lane_id)
);

CREATE INDEX ix_network_route_template_legs_lane
    ON network.route_template_legs(lane_id);

ALTER TABLE network.audit_logs
    DROP CONSTRAINT ck_network_audit_entity_type;

ALTER TABLE network.audit_logs
    ADD CONSTRAINT ck_network_audit_entity_type
    CHECK (entity_type IN (
        'REGION', 'HUB', 'SERVICE_AREA', 'COVERAGE',
        'LANE', 'LANE_SCHEDULE', 'ROUTE_TEMPLATE'
    ));

ALTER TABLE network.audit_logs
    DROP CONSTRAINT ck_network_audit_action;

ALTER TABLE network.audit_logs
    ADD CONSTRAINT ck_network_audit_action
    CHECK (action IN ('CREATED', 'UPDATED', 'ACTIVATED', 'DEACTIVATED', 'DELETED'));
