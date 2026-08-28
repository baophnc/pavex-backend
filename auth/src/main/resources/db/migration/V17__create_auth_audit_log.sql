CREATE TABLE auth.auth_audit_log
(
    id              UUID PRIMARY KEY,
    action          VARCHAR(96) NOT NULL,
    outcome         VARCHAR(16) NOT NULL,
    reason_code     VARCHAR(96),
    actor_user_id   UUID,
    subject_hash    VARCHAR(64),
    request_id      VARCHAR(128),
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_auth_audit_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX ix_auth_audit_created_at
    ON auth.auth_audit_log(created_at DESC);

CREATE INDEX ix_auth_audit_actor_created_at
    ON auth.auth_audit_log(actor_user_id, created_at DESC)
    WHERE actor_user_id IS NOT NULL;

CREATE INDEX ix_auth_audit_subject_created_at
    ON auth.auth_audit_log(subject_hash, created_at DESC)
    WHERE subject_hash IS NOT NULL;

CREATE INDEX ix_auth_audit_action_created_at
    ON auth.auth_audit_log(action, created_at DESC);
