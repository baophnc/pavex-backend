create schema if not exists identity;


create table identity.user_account
(
    id                    uuid primary key,
    email                 varchar(255) unique,
    phone                 varchar(32) unique,
    password_hash         varchar(512) not null,

    status                varchar(32) not null,
    profile_status        varchar(32) not null,

    email_verified        boolean not null default false,
    phone_verified        boolean not null default false,

    last_login_at         timestamptz,

    failed_login_attempts integer not null default 0,
    lockout_end_at        timestamptz,

    created_at            timestamptz not null,
    updated_at            timestamptz not null,

    version               bigint not null default 0
);


create table identity.user_profile
(
    user_id      uuid primary key
        references identity.user_account(id)
            on delete cascade,

    first_name   varchar(100),
    last_name    varchar(100),
    display_name varchar(200),

    gender       varchar(32) not null default 'Unspecified',

    date_of_birth date,

    avatar_url   varchar(2048),

    created_at   timestamptz not null,
    updated_at   timestamptz not null
);


create table identity.role
(
    id          uuid primary key,

    code        varchar(64) not null unique,
    name        varchar(128) not null,

    description varchar(512),

    is_system   boolean not null default true
);


create table identity.permission
(
    id          uuid primary key,

    code        varchar(128) not null unique,
    name        varchar(128) not null,

    description varchar(512)
);


create table identity.user_role
(
    user_id     uuid not null
        references identity.user_account(id)
            on delete cascade,

    role_id     uuid not null
        references identity.role(id)
            on delete cascade,

    assigned_at timestamptz not null,

    assigned_by uuid
        references identity.user_account(id)
                         on delete set null,

    primary key (user_id, role_id)
);


create table identity.role_permission
(
    role_id       uuid not null
        references identity.role(id)
            on delete cascade,

    permission_id uuid not null
        references identity.permission(id)
            on delete cascade,

    primary key (role_id, permission_id)
);


create table identity.refresh_session
(
    id                     uuid primary key,

    user_id                uuid not null
        references identity.user_account(id)
            on delete cascade,

    token_hash             varchar(128) not null unique,

    expires_at             timestamptz not null,
    created_at             timestamptz not null,
    revoked_at             timestamptz,

    replaced_by_session_id uuid
        references identity.refresh_session(id)
                                    on delete set null,

    created_by_ip          varchar(64),
    user_agent             varchar(512),

    version                bigint not null default 0
);


create index ix_refresh_session_user_active
    on identity.refresh_session(user_id, expires_at)
    where revoked_at is null;


create table identity.email_verification_token
(
    id         uuid primary key,

    user_id    uuid not null
        references identity.user_account(id)
            on delete cascade,

    token_hash varchar(128) not null unique,

    expires_at timestamptz not null,
    created_at timestamptz not null,
    used_at    timestamptz,

    version    bigint not null default 0
);


create index ix_email_verification_user
    on identity.email_verification_token(user_id, created_at desc);


create table identity.password_reset_token
(
    id         uuid primary key,

    user_id    uuid not null
        references identity.user_account(id)
            on delete cascade,

    token_hash varchar(128) not null unique,

    expires_at timestamptz not null,
    created_at timestamptz not null,
    used_at    timestamptz,

    version    bigint not null default 0
);


create index ix_password_reset_user
    on identity.password_reset_token(user_id, created_at desc);


create table identity.invitation_token
(
    id         uuid primary key,

    user_id    uuid not null
        references identity.user_account(id)
            on delete cascade,

    token_hash varchar(128) not null unique,

    expires_at timestamptz not null,
    created_at timestamptz not null,
    used_at    timestamptz,

    version    bigint not null default 0
);


create index ix_invitation_user
    on identity.invitation_token(user_id, created_at desc);


create table identity.external_identity
(
    id               uuid primary key,

    user_id          uuid not null
        references identity.user_account(id)
            on delete cascade,

    provider         varchar(50) not null,
    provider_user_id varchar(255) not null,

    email            varchar(320),

    created_at       timestamptz not null,

    constraint uq_external_identity_provider_user
        unique (provider, provider_user_id)
);


create index ix_external_identity_user
    on identity.external_identity(user_id);


create table identity.identity_audit_log
(
    id             uuid primary key,

    actor_user_id  uuid,
    target_user_id uuid,

    action         varchar(128) not null,

    metadata       jsonb,

    created_at     timestamptz not null
);


create index ix_identity_audit_target_created
    on identity.identity_audit_log(target_user_id, created_at desc);


create index ix_identity_audit_action_created
    on identity.identity_audit_log(action, created_at desc);