CREATE TABLE operations.operational_assignments (
    id UUID PRIMARY KEY,
    code VARCHAR(24) NOT NULL UNIQUE,
    shipment_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    route_leg_id UUID NULL,
    type VARCHAR(40) NOT NULL,
    required_kind VARCHAR(32) NOT NULL,
    hub_id UUID NOT NULL,
    assignee_workforce_member_id UUID NULL,
    status VARCHAR(20) NOT NULL,
    priority INTEGER NOT NULL,
    assigned_by_user_id UUID NULL,
    assigned_at TIMESTAMPTZ NULL,
    accepted_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    resolution_note VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operational_assignment_assignee
        FOREIGN KEY (assignee_workforce_member_id)
        REFERENCES operations.workforce_members(id)
        ON DELETE RESTRICT,
    CONSTRAINT uq_operational_assignment_shipment_sequence UNIQUE (shipment_id, sequence),
    CONSTRAINT ck_operational_assignment_sequence CHECK (sequence > 0),
    CONSTRAINT ck_operational_assignment_priority CHECK (priority BETWEEN 0 AND 100),
    CONSTRAINT ck_operational_assignment_type CHECK (
        type IN ('PICKUP','ORIGIN_HUB_HANDLING','LINE_HAUL','DESTINATION_HUB_HANDLING','LAST_MILE_DELIVERY')
    ),
    CONSTRAINT ck_operational_assignment_required_kind CHECK (
        required_kind IN ('COURIER','WAREHOUSE_OPERATOR','DISPATCHER','LINE_HAUL_DRIVER')
    ),
    CONSTRAINT ck_operational_assignment_status CHECK (
        status IN ('PENDING','ASSIGNED','ACCEPTED','REJECTED','IN_PROGRESS','COMPLETED','CANCELLED')
    )
);

CREATE INDEX ix_operational_assignment_assignee_status_priority
    ON operations.operational_assignments (assignee_workforce_member_id, status, priority DESC)
    WHERE assignee_workforce_member_id IS NOT NULL;

CREATE INDEX ix_operational_assignment_hub_kind_status_priority
    ON operations.operational_assignments (hub_id, required_kind, status, priority DESC);

CREATE INDEX ix_operational_assignment_shipment
    ON operations.operational_assignments (shipment_id, sequence);

CREATE INDEX ix_operational_assignment_route_leg
    ON operations.operational_assignments (route_leg_id)
    WHERE route_leg_id IS NOT NULL;
