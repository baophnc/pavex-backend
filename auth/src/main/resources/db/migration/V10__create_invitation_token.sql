CREATE TABLE auth.invitation_token
(
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL
        REFERENCES auth.user_account(id)
            ON DELETE CASCADE,

    token_hash  VARCHAR(128) NOT NULL UNIQUE,

    expires_at  TIMESTAMPTZ NOT NULL,

    used_at     TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_invitation_token_user_id
    ON auth.invitation_token(user_id);

CREATE INDEX ix_invitation_token_user_created_at
    ON auth.invitation_token(user_id, created_at);

CREATE INDEX ix_invitation_token_active
    ON auth.invitation_token(user_id, expires_at)
    WHERE used_at IS NULL;
