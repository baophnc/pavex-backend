CREATE SCHEMA IF NOT EXISTS notifications;

-- One message in one person's inbox. Same shape as the .NET module the client portal was built
-- against; sender columns are set for messages people send each other.
CREATE TABLE notifications.user_notifications (
    id                 UUID PRIMARY KEY,
    recipient_user_id  UUID          NOT NULL,
    code               VARCHAR(100)  NOT NULL,
    category           VARCHAR(20)   NOT NULL,
    severity           VARCHAR(20)   NOT NULL,
    title              VARCHAR(160)  NOT NULL,
    body               VARCHAR(1000) NOT NULL,
    action_url         VARCHAR(500),
    subject_type       VARCHAR(80),
    subject_id         VARCHAR(100),
    deduplication_key  VARCHAR(160),
    sender_user_id     UUID,
    message_id         UUID,
    created_at         TIMESTAMPTZ   NOT NULL,
    read_at            TIMESTAMPTZ,
    CONSTRAINT ck_notification_category CHECK (category IN ('Shipment', 'Operations', 'Account', 'Billing', 'System')),
    CONSTRAINT ck_notification_severity CHECK (severity IN ('Info', 'Success', 'Warning', 'Critical')),
    CONSTRAINT ck_notification_action_url CHECK (action_url IS NULL OR (action_url LIKE '/%' AND action_url NOT LIKE '//%'))
);

CREATE INDEX ix_notification_inbox ON notifications.user_notifications (recipient_user_id, created_at DESC, id DESC);
CREATE INDEX ix_notification_unread ON notifications.user_notifications (recipient_user_id, created_at DESC) WHERE read_at IS NULL;
-- An event or a broadcast reaches a person once, however often it is delivered.
CREATE UNIQUE INDEX ux_notification_dedup ON notifications.user_notifications (recipient_user_id, deduplication_key)
    WHERE deduplication_key IS NOT NULL;

-- A message someone sent: to a hub's people, or to the shop behind a shipment. The copies land in
-- user_notifications with message_id set.
CREATE TABLE notifications.messages (
    id               UUID PRIMARY KEY,
    sender_user_id   UUID          NOT NULL,
    audience         VARCHAR(30)   NOT NULL,
    hub_id           UUID,
    tracking_code    VARCHAR(40),
    title            VARCHAR(160)  NOT NULL,
    body             VARCHAR(1000) NOT NULL,
    recipient_count  INTEGER       NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_message_sender ON notifications.messages (sender_user_id, created_at DESC);

-- Who to tell about a shipment, learned from shipment.created: the account that created it (the
-- shop), and the hubs it passes, which decide who may write to that shop about it.
CREATE TABLE notifications.shipments (
    shipment_id          UUID PRIMARY KEY,
    tracking_code        VARCHAR(40)  NOT NULL UNIQUE,
    created_by_user_id   UUID         NOT NULL,
    channel              VARCHAR(30)  NOT NULL,
    origin_hub_id        UUID,
    destination_hub_id   UUID,
    created_at           TIMESTAMPTZ  NOT NULL
);
