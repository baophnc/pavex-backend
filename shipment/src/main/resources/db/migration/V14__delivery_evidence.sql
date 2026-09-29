-- Proof-of-delivery photos and signatures uploaded by the courier app. A delivery attempt
-- references one by "evidence:<id>".
CREATE TABLE shipments.delivery_evidence (
    id UUID PRIMARY KEY,
    uploaded_by_user_id UUID NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0 AND size_bytes <= 3145728),
    sha256 VARCHAR(64) NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_delivery_evidence_uploader ON shipments.delivery_evidence (uploaded_by_user_id, created_at DESC);
