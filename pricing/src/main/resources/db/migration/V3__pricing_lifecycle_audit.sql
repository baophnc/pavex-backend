CREATE TABLE pricing.audit_logs (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL,
    actor_user_id UUID NOT NULL,
    before_json TEXT NULL,
    after_json TEXT NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_pricing_audit_entity_type CHECK (entity_type IN ('RATE_PLAN', 'RATE_RULE')),
    CONSTRAINT ck_pricing_audit_action CHECK (
        action IN ('CREATED', 'UPDATED', 'RULE_UPSERTED', 'RULE_DELETED', 'REVISION_CREATED', 'ACTIVATED', 'RETIRED', 'DELETED')
    )
);

CREATE INDEX ix_pricing_audit_entity
    ON pricing.audit_logs(entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_pricing_audit_actor
    ON pricing.audit_logs(actor_user_id, occurred_at DESC);
CREATE INDEX ix_pricing_audit_occurred
    ON pricing.audit_logs(occurred_at DESC);
