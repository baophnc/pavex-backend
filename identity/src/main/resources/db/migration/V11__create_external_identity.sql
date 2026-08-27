CREATE TABLE identity.external_identity
(
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL
        REFERENCES identity.user_account(id)
            ON DELETE CASCADE,

    provider            VARCHAR(50) NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,

    email               VARCHAR(320),

    created_at          TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_external_identity_provider_user
    ON identity.external_identity(
        provider,
        provider_user_id
    );

CREATE UNIQUE INDEX ux_external_identity_user_provider
    ON identity.external_identity(
        user_id,
        provider
    );

CREATE INDEX ix_external_identity_user_id
    ON identity.external_identity(user_id);
