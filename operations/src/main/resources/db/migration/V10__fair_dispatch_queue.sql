-- Redispatch took the 25 highest-priority waiting jobs every cycle, so when more than 25 had no
-- one to take them the same jobs were retried forever and the rest were never looked at.
-- The worker now takes the jobs it has tried least recently: a job that found nobody moves to the
-- back of the queue, and a job whose hub gains someone (check-in, back to available, released work)
-- is cleared to the front again. Changing this column alone records no assignment event.
ALTER TABLE operations.operational_assignments
    ADD COLUMN last_dispatch_attempt_at TIMESTAMPTZ;

CREATE INDEX ix_operational_assignment_dispatch_queue
    ON operations.operational_assignments (last_dispatch_attempt_at ASC NULLS FIRST, priority DESC, created_at ASC, id ASC)
    WHERE status IN ('PENDING', 'READY', 'REJECTED');

-- Candidate lookup now starts from the people of one hub instead of every member of a kind.
CREATE INDEX IF NOT EXISTS ix_operations_active_membership_hub_member
    ON operations.hub_memberships (hub_id, workforce_member_id)
    WHERE is_active = TRUE;

-- Assigned jobs nobody accepted in time are handed to someone else.
CREATE INDEX ix_operational_assignment_unaccepted
    ON operations.operational_assignments (assigned_at)
    WHERE status = 'ASSIGNED';
