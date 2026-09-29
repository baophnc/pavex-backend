ALTER TABLE pricing.audit_logs DROP CONSTRAINT ck_pricing_audit_action;
ALTER TABLE pricing.audit_logs ADD CONSTRAINT ck_pricing_audit_action CHECK (
    action IN (
        'CREATED', 'UPDATED', 'RULE_UPSERTED', 'RULE_DELETED', 'REVISION_CREATED',
        'ACTIVATED', 'RETIRED', 'DELETED', 'SUPERSEDED'
    )
);
