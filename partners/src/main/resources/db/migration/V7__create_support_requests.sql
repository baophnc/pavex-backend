-- Requests customers and visitors send from the public contact, support, claim and career forms.
-- Customer care works them from the management portal inbox.
CREATE TABLE partners.support_requests (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(160) NOT NULL,
    phone VARCHAR(20),
    topic VARCHAR(100) NOT NULL,
    tracking_code VARCHAR(40),
    incident_date DATE,
    claimed_amount BIGINT,
    portfolio_url VARCHAR(500),
    message VARCHAR(2000) NOT NULL,
    submitted_by_user_id UUID,
    assignee_user_id UUID,
    resolution_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ux_support_request_code UNIQUE (code),
    CONSTRAINT ck_support_request_kind CHECK (kind IN ('CONTACT', 'SUPPORT', 'CLAIM', 'CAREER')),
    CONSTRAINT ck_support_request_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_support_request_claim CHECK (
        kind <> 'CLAIM' OR (tracking_code IS NOT NULL AND incident_date IS NOT NULL)
    ),
    CONSTRAINT ck_support_request_amount CHECK (claimed_amount IS NULL OR claimed_amount >= 0)
);

CREATE INDEX ix_support_requests_status_created ON partners.support_requests (status, created_at DESC);
CREATE INDEX ix_support_requests_email_created ON partners.support_requests (email, created_at DESC);
CREATE INDEX ix_support_requests_tracking_code ON partners.support_requests (tracking_code)
    WHERE tracking_code IS NOT NULL;

-- Claim evidence (images or PDF), at most five files of 5 MB each per request.
CREATE TABLE partners.support_request_attachments (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES partners.support_requests (id) ON DELETE CASCADE,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_support_request_attachments_request ON partners.support_request_attachments (request_id, created_at);

-- What happened to a request and who did it, shown as its history in the inbox.
CREATE TABLE partners.support_request_events (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES partners.support_requests (id) ON DELETE CASCADE,
    actor_user_id UUID,
    action VARCHAR(30) NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_support_request_event_action CHECK (action IN ('SUBMITTED', 'STATUS_CHANGED', 'ASSIGNED', 'UNASSIGNED'))
);

CREATE INDEX ix_support_request_events_request ON partners.support_request_events (request_id, created_at);
