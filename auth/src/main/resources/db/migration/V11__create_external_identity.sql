CREATE TABLE auth.external_identity
(
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL
        REFERENCES auth.user_account(id)
            ON DELETE CASCADE,

    provider            VARCHAR(50) NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,

    email               VARCHAR(320),

    created_at          TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_external_identity_provider_user
    ON auth.external_identity(
        provider,
        provider_user_id
    );

CREATE UNIQUE INDEX ux_external_identity_user_provider
    ON auth.external_identity(
        user_id,
        provider
    );

CREATE INDEX ix_external_identity_user_id
    ON auth.external_identity(user_id);
