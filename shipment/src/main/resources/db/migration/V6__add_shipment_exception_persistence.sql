ALTER TABLE shipments.shipments
    ADD COLUMN IF NOT EXISTS is_on_hold BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS hold_actor_user_id UUID,
    ADD COLUMN IF NOT EXISTS hold_hub_id UUID,
    ADD COLUMN IF NOT EXISTS held_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS has_pending_weight_review BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS shipments.shipment_exception_requests (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE CASCADE,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    requested_by_user_id UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    routing_required BOOLEAN NOT NULL DEFAULT FALSE,
    forward_contact_name VARCHAR(120),
    forward_contact_phone VARCHAR(20),
    forward_address_line VARCHAR(500),
    forward_province_code VARCHAR(20),
    forward_ward_code VARCHAR(20),
    completed_by_user_id UUID,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_shipment_exception_kind CHECK (kind IN ('RETURN', 'FORWARD', 'CANCELLATION')),
    CONSTRAINT ck_shipment_exception_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED')),
    CONSTRAINT ck_shipment_exception_reason CHECK (char_length(btrim(reason)) BETWEEN 1 AND 1000)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shipment_exception_active
    ON shipments.shipment_exception_requests(shipment_id)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'PROCESSING');
CREATE INDEX IF NOT EXISTS ix_shipment_exception_history
    ON shipments.shipment_exception_requests(shipment_id, requested_at DESC);

CREATE TABLE IF NOT EXISTS shipments.shipment_weight_adjustments (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE CASCADE,
    parcel_id UUID NOT NULL REFERENCES shipments.parcels(id) ON DELETE CASCADE,
    old_actual_weight_kg NUMERIC(12,3) NOT NULL,
    new_actual_weight_kg NUMERIC(12,3) NOT NULL,
    old_chargeable_weight_kg NUMERIC(12,3) NOT NULL,
    new_chargeable_weight_kg NUMERIC(12,3) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor_user_id UUID NOT NULL,
    hub_id UUID,
    adjusted_at TIMESTAMPTZ NOT NULL,
    review_completed BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipment_weight_adjustment_positive CHECK (
        old_actual_weight_kg > 0 AND new_actual_weight_kg > 0 AND
        old_chargeable_weight_kg > 0 AND new_chargeable_weight_kg > 0
    ),
    CONSTRAINT ck_shipment_weight_adjustment_reason CHECK (char_length(btrim(reason)) BETWEEN 1 AND 1000)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shipment_weight_review_pending
    ON shipments.shipment_weight_adjustments(shipment_id)
    WHERE review_completed = FALSE;
CREATE INDEX IF NOT EXISTS ix_shipment_weight_adjustment_history
    ON shipments.shipment_weight_adjustments(shipment_id, adjusted_at DESC);
