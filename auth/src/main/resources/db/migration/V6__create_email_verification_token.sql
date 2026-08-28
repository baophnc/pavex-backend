CREATE TABLE auth.email_verification_token
(
    id          UUID PRIMARY KEY,

    user_id     UUID NOT NULL
        REFERENCES auth.user_account(id)
        ON DELETE CASCADE,

    token_hash  VARCHAR(128) NOT NULL UNIQUE,

    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);

CREATE INDEX ix_email_verification_token_user_id
    ON auth.email_verification_token(user_id);

CREATE INDEX ix_email_verification_token_user_created
    ON auth.email_verification_token(user_id, created_at);
