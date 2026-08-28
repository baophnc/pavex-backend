CREATE TABLE auth.refresh_session
(
    id                      UUID PRIMARY KEY,

    user_id                 UUID NOT NULL
        REFERENCES auth.user_account(id)
            ON DELETE CASCADE,

    token_hash              VARCHAR(128) NOT NULL UNIQUE,

    expires_at              TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,

    revoked_at              TIMESTAMPTZ,

    replaced_by_session_id  UUID
        REFERENCES auth.refresh_session(id)
                                     ON DELETE SET NULL,

    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX ix_refresh_session_user_id
    ON auth.refresh_session(user_id);

CREATE INDEX ix_refresh_session_user_active
    ON auth.refresh_session(user_id, expires_at)
    WHERE revoked_at IS NULL;