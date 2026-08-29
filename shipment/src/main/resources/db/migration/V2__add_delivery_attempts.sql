CREATE TABLE shipments.delivery_attempts (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE RESTRICT,
    parcel_id UUID NOT NULL REFERENCES shipments.parcels(id) ON DELETE RESTRICT,
    actor_user_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(40),
    proof_method VARCHAR(20),
    recipient_name VARCHAR(200),
    evidence_reference VARCHAR(500),
    note VARCHAR(500),
    latitude NUMERIC(9,6),
    longitude NUMERIC(9,6),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ux_delivery_attempt_parcel_key UNIQUE (parcel_id, idempotency_key),
    CONSTRAINT ux_delivery_attempt_parcel_number UNIQUE (parcel_id, attempt_number),
    CONSTRAINT ck_delivery_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_delivery_attempt_key CHECK (char_length(idempotency_key) BETWEEN 8 AND 100),
    CONSTRAINT ck_delivery_attempt_fingerprint CHECK (char_length(request_fingerprint) = 64),
    CONSTRAINT ck_delivery_attempt_outcome CHECK (outcome IN ('DELIVERED', 'FAILED')),
    CONSTRAINT ck_delivery_attempt_failure_reason CHECK (
        failure_reason IS NULL OR failure_reason IN (
            'RECIPIENT_UNAVAILABLE', 'RECIPIENT_REFUSED', 'INVALID_ADDRESS', 'UNABLE_TO_CONTACT',
            'PAYMENT_NOT_COLLECTED', 'PARCEL_DAMAGED', 'UNSAFE_LOCATION', 'OTHER'
        )
    ),
    CONSTRAINT ck_delivery_attempt_proof_method CHECK (
        proof_method IS NULL OR proof_method IN ('OTP', 'SIGNATURE', 'PHOTO')
    ),
    CONSTRAINT ck_delivery_attempt_location CHECK (
        (latitude IS NULL AND longitude IS NULL) OR
        (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_delivery_attempt_payload CHECK (
        (outcome = 'DELIVERED' AND failure_reason IS NULL AND proof_method IS NOT NULL AND recipient_name IS NOT NULL AND evidence_reference IS NOT NULL)
        OR
        (outcome = 'FAILED' AND failure_reason IS NOT NULL AND proof_method IS NULL AND recipient_name IS NULL AND evidence_reference IS NULL)
    )
);

CREATE INDEX ix_delivery_attempt_shipment_occurred
    ON shipments.delivery_attempts(shipment_id, occurred_at DESC);
CREATE INDEX ix_delivery_attempt_actor_occurred
    ON shipments.delivery_attempts(actor_user_id, occurred_at DESC);
