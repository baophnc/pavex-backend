CREATE TABLE operations.work_shifts (
    id UUID PRIMARY KEY,
    hub_id UUID NOT NULL,
    code VARCHAR(24) NOT NULL,
    name VARCHAR(100) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by_user_id UUID NOT NULL,
    cancelled_by_user_id UUID NULL,
    cancel_reason VARCHAR(500) NULL,
    cancelled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_operations_work_shift_code UNIQUE (code),
    CONSTRAINT ck_operations_work_shift_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    CONSTRAINT ck_operations_work_shift_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_operations_work_shift_duration CHECK (ends_at <= starts_at + INTERVAL '24 hours'),
    CONSTRAINT ck_operations_work_shift_cancelled CHECK (
        (status = 'SCHEDULED' AND cancelled_by_user_id IS NULL AND cancel_reason IS NULL AND cancelled_at IS NULL)
        OR
        (status = 'CANCELLED' AND cancelled_by_user_id IS NOT NULL AND cancel_reason IS NOT NULL AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX ix_operations_work_shifts_hub_status_start
    ON operations.work_shifts(hub_id, status, starts_at);
CREATE INDEX ix_operations_work_shifts_window
    ON operations.work_shifts(starts_at, ends_at);

CREATE TABLE operations.workforce_shift_assignments (
    id UUID PRIMARY KEY,
    shift_id UUID NOT NULL REFERENCES operations.work_shifts(id),
    workforce_member_id UUID NOT NULL REFERENCES operations.workforce_members(id),
    status VARCHAR(20) NOT NULL,
    assigned_by_user_id UUID NOT NULL,
    status_changed_by_user_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ NULL,
    checked_out_at TIMESTAMPTZ NULL,
    marked_absent_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    note VARCHAR(500) NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_operations_shift_member UNIQUE (shift_id, workforce_member_id),
    CONSTRAINT ck_operations_shift_assignment_status CHECK (
        status IN ('SCHEDULED', 'CHECKED_IN', 'CHECKED_OUT', 'ABSENT', 'CANCELLED')
    )
);

CREATE INDEX ix_operations_shift_assignment_member_status
    ON operations.workforce_shift_assignments(workforce_member_id, status);
CREATE INDEX ix_operations_shift_assignment_shift_status
    ON operations.workforce_shift_assignments(shift_id, status);
