CREATE TABLE identity.user_role
(
    user_id      UUID NOT NULL
        REFERENCES identity.user_account(id)
        ON DELETE CASCADE,

    role_id      UUID NOT NULL
        REFERENCES identity.role(id)
        ON DELETE CASCADE,

    assigned_at  TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (user_id, role_id)
);


CREATE TABLE identity.role_permission
(
    role_id        UUID NOT NULL
        REFERENCES identity.role(id)
        ON DELETE CASCADE,

    permission_id  UUID NOT NULL
        REFERENCES identity.permission(id)
        ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_id)
);


CREATE INDEX ix_user_role_user
    ON identity.user_role(user_id);

CREATE INDEX ix_role_permission_role
    ON identity.role_permission(role_id);
