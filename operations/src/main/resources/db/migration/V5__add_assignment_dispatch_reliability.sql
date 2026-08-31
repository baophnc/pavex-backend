CREATE TABLE operations.operational_assignment_event_logs (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES operations.operational_assignments(id) ON DELETE CASCADE,
    shipment_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    workforce_member_id UUID NULL,
    actor_user_id UUID NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_operational_assignment_event_type CHECK (
        event_type IN ('PLANNED','ASSIGNED','REASSIGNED','ACCEPTED','REJECTED','STARTED','COMPLETED','CANCELLED','REQUEUED')
    )
);

CREATE INDEX ix_operational_assignment_event_assignment_time
    ON operations.operational_assignment_event_logs (assignment_id, occurred_at, id);
CREATE INDEX ix_operational_assignment_event_shipment_time
    ON operations.operational_assignment_event_logs (shipment_id, occurred_at, id);
CREATE INDEX ix_operational_assignment_event_type_member
    ON operations.operational_assignment_event_logs (event_type, workforce_member_id)
    WHERE workforce_member_id IS NOT NULL;

CREATE TABLE operations.outbox_messages (
    id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    dead_lettered_at TIMESTAMPTZ NULL,
    discarded_at TIMESTAMPTZ NULL,
    discarded_by_user_id UUID NULL,
    discard_reason VARCHAR(500) NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(2000) NULL,
    lock_id UUID NULL,
    locked_until TIMESTAMPTZ NULL,
    CONSTRAINT ck_operations_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_operations_outbox_delivery_state
    ON operations.outbox_messages (processed_at, dead_lettered_at, discarded_at, available_at, occurred_at);
CREATE INDEX ix_operations_outbox_aggregate_time
    ON operations.outbox_messages (aggregate_type, aggregate_id, occurred_at, id);
CREATE INDEX ix_operations_outbox_lock_expiry
    ON operations.outbox_messages (locked_until)
    WHERE processed_at IS NULL AND dead_lettered_at IS NULL AND discarded_at IS NULL;

CREATE OR REPLACE FUNCTION operations.record_assignment_event(
    p_event_type VARCHAR,
    p_assignment_id UUID,
    p_shipment_id UUID,
    p_previous_status VARCHAR,
    p_new_status VARCHAR,
    p_workforce_member_id UUID,
    p_actor_user_id UUID,
    p_reason VARCHAR,
    p_occurred_at TIMESTAMPTZ
) RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_event_id UUID := gen_random_uuid();
    v_payload JSONB;
BEGIN
    v_payload := jsonb_build_object(
        'eventId', v_event_id,
        'eventType', p_event_type,
        'assignmentId', p_assignment_id,
        'shipmentId', p_shipment_id,
        'previousStatus', p_previous_status,
        'newStatus', p_new_status,
        'workforceMemberId', p_workforce_member_id,
        'actorUserId', p_actor_user_id,
        'reason', p_reason,
        'occurredAt', p_occurred_at
    );

    INSERT INTO operations.operational_assignment_event_logs (
        id, assignment_id, shipment_id, event_type, previous_status, new_status,
        workforce_member_id, actor_user_id, reason, occurred_at
    ) VALUES (
        v_event_id, p_assignment_id, p_shipment_id, p_event_type, p_previous_status, p_new_status,
        p_workforce_member_id, p_actor_user_id, p_reason, p_occurred_at
    );

    INSERT INTO operations.outbox_messages (
        id, event_type, aggregate_type, aggregate_id, payload, occurred_at, available_at
    ) VALUES (
        v_event_id,
        'operations.assignment.' || lower(p_event_type),
        'OperationalAssignment',
        p_assignment_id,
        v_payload,
        p_occurred_at,
        p_occurred_at
    );
END;
$$;

CREATE OR REPLACE FUNCTION operations.capture_assignment_changes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_member_id UUID;
    v_actor_id UUID;
    v_event_type VARCHAR(30);
    v_time TIMESTAMPTZ;
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM operations.record_assignment_event(
            'PLANNED', NEW.id, NEW.shipment_id, NULL, 'PENDING', NULL, NULL, NULL, NEW.created_at
        );

        IF NEW.status = 'ASSIGNED' THEN
            PERFORM operations.record_assignment_event(
                'ASSIGNED', NEW.id, NEW.shipment_id, 'PENDING', 'ASSIGNED',
                NEW.assignee_workforce_member_id, NEW.assigned_by_user_id, NULL,
                COALESCE(NEW.assigned_at, NEW.updated_at)
            );
        ELSIF NEW.status <> 'PENDING' THEN
            RAISE EXCEPTION 'Unexpected initial operational assignment status: %', NEW.status;
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.status = OLD.status AND NEW.assignee_workforce_member_id IS NOT DISTINCT FROM OLD.assignee_workforce_member_id THEN
        RETURN NEW;
    END IF;

    v_time := NEW.updated_at;
    v_member_id := COALESCE(NEW.assignee_workforce_member_id, OLD.assignee_workforce_member_id);
    v_actor_id := NULL;

    IF NEW.status = 'ASSIGNED' THEN
        v_actor_id := NEW.assigned_by_user_id;
        v_event_type := CASE
            WHEN OLD.status = 'PENDING' AND OLD.assignee_workforce_member_id IS NULL THEN 'ASSIGNED'
            ELSE 'REASSIGNED'
        END;
    ELSIF NEW.status = 'ACCEPTED' THEN
        v_event_type := 'ACCEPTED';
    ELSIF NEW.status = 'REJECTED' THEN
        v_event_type := 'REJECTED';
    ELSIF NEW.status = 'IN_PROGRESS' THEN
        v_event_type := 'STARTED';
    ELSIF NEW.status = 'COMPLETED' THEN
        v_event_type := 'COMPLETED';
    ELSIF NEW.status = 'CANCELLED' THEN
        v_event_type := 'CANCELLED';
    ELSIF NEW.status = 'PENDING' AND OLD.status IN ('ASSIGNED','ACCEPTED') THEN
        v_event_type := 'REQUEUED';
    ELSE
        RETURN NEW;
    END IF;

    IF v_actor_id IS NULL AND v_member_id IS NOT NULL AND v_event_type IN ('ACCEPTED','REJECTED','STARTED','COMPLETED') THEN
        SELECT user_id INTO v_actor_id
        FROM operations.workforce_members
        WHERE id = v_member_id;
    END IF;

    PERFORM operations.record_assignment_event(
        v_event_type,
        NEW.id,
        NEW.shipment_id,
        OLD.status,
        NEW.status,
        v_member_id,
        v_actor_id,
        NEW.resolution_note,
        v_time
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_operational_assignment_dispatch_events
AFTER INSERT OR UPDATE ON operations.operational_assignments
FOR EACH ROW EXECUTE FUNCTION operations.capture_assignment_changes();
