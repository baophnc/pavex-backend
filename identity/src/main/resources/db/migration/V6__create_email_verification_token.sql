CREATE TABLE identity.email_verification_token
(
    id          UUID PRIMARY KEY,

    user_id     UUID NOT NULL
        REFERENCES identity.user_account(id)
        ON DELETE CASCADE,

    token_hash  VARCHAR(128) NOT NULL UNIQUE,

    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX ix_email_verification_token_user_id
    ON identity.email_verification_token(user_id);

CREATE INDEX ix_email_verification_token_user_created
    ON identity.email_verification_token(user_id, created_at);
