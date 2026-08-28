CREATE TABLE auth.user_role
(
    user_id      UUID NOT NULL
        REFERENCES auth.user_account(id)
        ON DELETE CASCADE,

    role_id      UUID NOT NULL
        REFERENCES auth.role(id)
        ON DELETE CASCADE,

    assigned_at  TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (user_id, role_id)
);


CREATE TABLE auth.role_permission
(
    role_id        UUID NOT NULL
        REFERENCES auth.role(id)
        ON DELETE CASCADE,

    permission_id  UUID NOT NULL
        REFERENCES auth.permission(id)
        ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_id)
);


CREATE INDEX ix_user_role_user
    ON auth.user_role(user_id);

CREATE INDEX ix_role_permission_role
    ON auth.role_permission(role_id);
