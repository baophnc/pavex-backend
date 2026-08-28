CREATE TABLE identity.user_account
(
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    profile_status VARCHAR(32) NOT NULL DEFAULT 'INCOMPLETE',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    authorization_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_identity_user_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),

    CONSTRAINT ck_identity_profile_status
        CHECK (profile_status IN (
            'INCOMPLETE',
            'COMPLETED',
            'PENDING_VERIFICATION',
            'REJECTED'
        ))
);

CREATE TABLE identity.user_profile
(
    user_id UUID PRIMARY KEY
        REFERENCES identity.user_account(id)
            ON DELETE CASCADE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(200),
    gender VARCHAR(32) NOT NULL DEFAULT 'UNSPECIFIED',
    date_of_birth DATE,
    avatar_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_identity_user_profile_gender
        CHECK (gender IN ('UNSPECIFIED', 'MALE', 'FEMALE', 'OTHER'))
);

CREATE TABLE identity.role
(
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    is_system BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE identity.permission
(
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512)
);

CREATE TABLE identity.user_role
(
    user_id UUID NOT NULL
        REFERENCES identity.user_account(id)
            ON DELETE CASCADE,
    role_id UUID NOT NULL
        REFERENCES identity.role(id)
            ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL,
    assigned_by UUID
        REFERENCES identity.user_account(id)
            ON DELETE SET NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE identity.role_permission
(
    role_id UUID NOT NULL
        REFERENCES identity.role(id)
            ON DELETE CASCADE,
    permission_id UUID NOT NULL
        REFERENCES identity.permission(id)
            ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_identity_user_account_status
    ON identity.user_account(status);

CREATE INDEX ix_identity_user_role_user
    ON identity.user_role(user_id);

CREATE INDEX ix_identity_role_permission_role
    ON identity.role_permission(role_id);

-- Preserve data from the first split checkpoint. The legacy table remains
-- temporarily so this migration is safe for already-created local databases.
INSERT INTO identity.user_account (
    id,
    email,
    status,
    profile_status,
    email_verified,
    authorization_version,
    created_at,
    updated_at,
    version
)
SELECT
    user_id,
    email,
    status,
    'INCOMPLETE',
    email_verified,
    authorization_version,
    created_at,
    updated_at,
    version
FROM identity.identity_profile
ON CONFLICT (id) DO NOTHING;

INSERT INTO identity.user_profile (
    user_id,
    first_name,
    last_name,
    display_name,
    gender,
    date_of_birth,
    avatar_url,
    created_at,
    updated_at
)
SELECT
    user_id,
    first_name,
    last_name,
    display_name,
    'UNSPECIFIED',
    NULL,
    avatar_url,
    created_at,
    updated_at
FROM identity.identity_profile
WHERE first_name IS NOT NULL
   OR last_name IS NOT NULL
   OR display_name IS NOT NULL
   OR avatar_url IS NOT NULL
ON CONFLICT (user_id) DO NOTHING;
