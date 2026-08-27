CREATE SCHEMA IF NOT EXISTS identity;


CREATE TABLE identity.user_account
(
    id                     UUID PRIMARY KEY,
    email                  VARCHAR(255) NOT NULL UNIQUE,
    password_hash          VARCHAR(512) NOT NULL,

    status                 VARCHAR(32) NOT NULL,

    email_verified         BOOLEAN NOT NULL DEFAULT FALSE,

    failed_login_attempts  INTEGER NOT NULL DEFAULT 0,
    lockout_end_at         TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,

    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,

    version                BIGINT NOT NULL DEFAULT 0
);


CREATE TABLE identity.user_profile
(
    user_id       UUID PRIMARY KEY
        REFERENCES identity.user_account(id)
            ON DELETE CASCADE,

    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    display_name  VARCHAR(200),

    gender        VARCHAR(32) NOT NULL DEFAULT 'UNSPECIFIED',

    date_of_birth DATE,

    avatar_url    VARCHAR(2048),

    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);


CREATE TABLE identity.role
(
    id           UUID PRIMARY KEY,

    code         VARCHAR(64) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,

    description  VARCHAR(512),

    is_system    BOOLEAN NOT NULL DEFAULT TRUE
);


CREATE TABLE identity.permission
(
    id           UUID PRIMARY KEY,

    code         VARCHAR(128) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,

    description  VARCHAR(512)
);