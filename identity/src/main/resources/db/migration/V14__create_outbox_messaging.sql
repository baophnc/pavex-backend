CREATE TABLE identity.outbox_message
(
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(80),
    aggregate_id UUID,
    event_type VARCHAR(160) NOT NULL,
    event_version INTEGER NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload_ciphertext TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_outbox_event_version_positive
        CHECK (event_version > 0),

    CONSTRAINT ck_outbox_attempts_non_negative
        CHECK (attempts >= 0),

    CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'DEAD'))
);

CREATE INDEX ix_outbox_pending
    ON identity.outbox_message(status, next_attempt_at, occurred_at)
    WHERE status = 'PENDING';

CREATE INDEX ix_outbox_published_at
    ON identity.outbox_message(published_at)
    WHERE status = 'PUBLISHED';

CREATE TABLE identity.consumed_message
(
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    consumer VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ux_consumed_message_consumer
        UNIQUE (message_id, consumer)
);

CREATE INDEX ix_consumed_message_processed_at
    ON identity.consumed_message(processed_at);
