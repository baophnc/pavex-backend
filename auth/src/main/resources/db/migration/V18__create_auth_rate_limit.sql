CREATE TABLE auth.auth_rate_limit
(
    policy             VARCHAR(64) NOT NULL,
    key_hash           VARCHAR(64) NOT NULL,
    window_started_at  TIMESTAMPTZ NOT NULL,
    request_count      INTEGER NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (policy, key_hash),

    CONSTRAINT ck_auth_rate_limit_count
        CHECK (request_count > 0)
);

CREATE INDEX ix_auth_rate_limit_expires_at
    ON auth.auth_rate_limit(expires_at);
