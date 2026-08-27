ALTER TABLE identity.refresh_session
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN user_agent VARCHAR(512);
