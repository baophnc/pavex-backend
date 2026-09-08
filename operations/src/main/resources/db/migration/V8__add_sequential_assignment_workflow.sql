ALTER TABLE operations.operational_assignments
    DROP CONSTRAINT ck_operational_assignment_type,
    ADD CONSTRAINT ck_operational_assignment_type CHECK (
        type IN ('PICKUP','ORIGIN_HUB_HANDLING','LINE_HAUL','TRANSIT_HUB_HANDLING','DESTINATION_HUB_HANDLING','LAST_MILE_DELIVERY')
    );

ALTER TABLE operations.operational_assignments
    DROP CONSTRAINT ck_operational_assignment_status,
    ADD CONSTRAINT ck_operational_assignment_status CHECK (
        status IN ('BLOCKED','READY','PENDING','ASSIGNED','ACCEPTED','REJECTED','IN_PROGRESS','COMPLETED','CANCELLED')
    );

ALTER TABLE operations.operational_assignment_event_logs
    DROP CONSTRAINT ck_operational_assignment_event_type,
    ADD CONSTRAINT ck_operational_assignment_event_type CHECK (
        event_type IN ('PLANNED','ACTIVATED','ASSIGNED','REASSIGNED','ACCEPTED','REJECTED','STARTED','COMPLETED','CANCELLED','REQUEUED')
    );

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
            'PLANNED', NEW.id, NEW.shipment_id, NULL, NEW.status,
            NULL, NULL, NULL, NEW.created_at
        );

        IF NEW.status = 'ASSIGNED' THEN
            PERFORM operations.record_assignment_event(
                'ASSIGNED', NEW.id, NEW.shipment_id, 'READY', 'ASSIGNED',
                NEW.assignee_workforce_member_id, NEW.assigned_by_user_id, NULL,
                COALESCE(NEW.assigned_at, NEW.updated_at)
            );
        ELSIF NEW.status NOT IN ('BLOCKED', 'READY', 'PENDING') THEN
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

    IF NEW.status = 'READY' AND OLD.status = 'BLOCKED' THEN
        v_event_type := 'ACTIVATED';
        v_member_id := NULL;
    ELSIF NEW.status = 'ASSIGNED' THEN
        v_actor_id := NEW.assigned_by_user_id;
        v_event_type := CASE
            WHEN OLD.status IN ('PENDING', 'READY') AND OLD.assignee_workforce_member_id IS NULL THEN 'ASSIGNED'
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
    ELSIF NEW.status IN ('PENDING', 'READY') AND OLD.status IN ('ASSIGNED','ACCEPTED') THEN
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
