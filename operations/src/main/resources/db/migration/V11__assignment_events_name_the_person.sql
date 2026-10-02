-- Assignment events name the person and the place, so Notifications can tell the courier, driver or
-- staff member directly ("you have a new pickup at HN-BD") without asking Operations who the
-- workforce member is. The function keeps its signature; only the payload grows.
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
    v_user_id UUID;
    v_hub_id UUID;
    v_assignment_type VARCHAR;
BEGIN
    SELECT user_id INTO v_user_id FROM operations.workforce_members WHERE id = p_workforce_member_id;
    SELECT hub_id, type INTO v_hub_id, v_assignment_type
    FROM operations.operational_assignments WHERE id = p_assignment_id;

    v_payload := jsonb_build_object(
        'eventId', v_event_id,
        'eventType', p_event_type,
        'assignmentId', p_assignment_id,
        'shipmentId', p_shipment_id,
        'previousStatus', p_previous_status,
        'newStatus', p_new_status,
        'workforceMemberId', p_workforce_member_id,
        'workforceUserId', v_user_id,
        'hubId', v_hub_id,
        'assignmentType', v_assignment_type,
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
