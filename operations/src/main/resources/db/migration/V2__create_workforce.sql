CREATE TABLE operations.workforce_members (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_operations_workforce_user UNIQUE (user_id),
    CONSTRAINT uq_operations_workforce_employee_code UNIQUE (employee_code),
    CONSTRAINT ck_operations_workforce_kind CHECK (
        kind IN ('COURIER', 'WAREHOUSE_OPERATOR', 'DISPATCHER', 'LINE_HAUL_DRIVER')
    ),
    CONSTRAINT ck_operations_workforce_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'OFFBOARDED')
    )
);

CREATE TABLE operations.hub_memberships (
    id UUID PRIMARY KEY,
    workforce_member_id UUID NOT NULL REFERENCES operations.workforce_members(id),
    hub_id UUID NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_operations_workforce_hub UNIQUE (workforce_member_id, hub_id),
    CONSTRAINT ck_operations_membership_unassigned CHECK (
        (is_active AND unassigned_at IS NULL) OR NOT is_active
    )
);

CREATE UNIQUE INDEX uq_operations_active_primary_hub
    ON operations.hub_memberships(workforce_member_id)
    WHERE is_active = TRUE AND is_primary = TRUE;

CREATE INDEX ix_operations_hub_memberships_hub
    ON operations.hub_memberships(hub_id, is_active);

CREATE TABLE operations.workforce_availabilities (
    workforce_member_id UUID PRIMARY KEY REFERENCES operations.workforce_members(id),
    status VARCHAR(24) NOT NULL,
    max_concurrent_assignments INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_operations_availability_status CHECK (
        status IN ('OFF_DUTY', 'AVAILABLE', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_operations_availability_capacity CHECK (
        max_concurrent_assignments BETWEEN 1 AND 100
    )
);

CREATE TABLE operations.workforce_audit_logs (
    id UUID PRIMARY KEY,
    workforce_member_id UUID NOT NULL REFERENCES operations.workforce_members(id),
    hub_id UUID NULL,
    actor_user_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    previous_value VARCHAR(100) NULL,
    new_value VARCHAR(100) NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operations_workforce_audit_action CHECK (
        action IN ('MEMBER_CREATED', 'STATUS_CHANGED', 'HUB_ASSIGNED', 'HUB_UNASSIGNED', 'CAPACITY_CHANGED')
    )
);

CREATE INDEX ix_operations_workforce_audit_member_time
    ON operations.workforce_audit_logs(workforce_member_id, occurred_at DESC);
