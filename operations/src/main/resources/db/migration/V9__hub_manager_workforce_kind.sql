-- Hub dispatchers were folded into the hub manager role, so the workforce kind
-- that onboarded them is now named for what they are.
ALTER TABLE operations.workforce_members DROP CONSTRAINT ck_operations_workforce_kind;
UPDATE operations.workforce_members SET kind = 'HUB_MANAGER' WHERE kind = 'DISPATCHER';
ALTER TABLE operations.workforce_members ADD CONSTRAINT ck_operations_workforce_kind CHECK (
    kind IN ('COURIER', 'WAREHOUSE_OPERATOR', 'HUB_MANAGER', 'LINE_HAUL_DRIVER')
);

ALTER TABLE operations.operational_assignments DROP CONSTRAINT ck_operational_assignment_required_kind;
UPDATE operations.operational_assignments SET required_kind = 'HUB_MANAGER' WHERE required_kind = 'DISPATCHER';
ALTER TABLE operations.operational_assignments ADD CONSTRAINT ck_operational_assignment_required_kind CHECK (
    required_kind IN ('COURIER', 'WAREHOUSE_OPERATOR', 'HUB_MANAGER', 'LINE_HAUL_DRIVER')
);
