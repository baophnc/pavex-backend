-- Patch 6: indexes that match the scoped admin read paths introduced after
-- authorization was moved into database predicates.

CREATE INDEX IF NOT EXISTS ix_operations_assignment_admin_scope
    ON operations.operational_assignments (hub_id, status, priority DESC, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS ix_operations_assignment_admin_global
    ON operations.operational_assignments (priority DESC, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS ix_operations_workforce_status_kind_employee
    ON operations.workforce_members (status, kind, employee_code, id);

CREATE INDEX IF NOT EXISTS ix_operations_active_membership_member_hub
    ON operations.hub_memberships (workforce_member_id, hub_id)
    WHERE is_active = TRUE;
