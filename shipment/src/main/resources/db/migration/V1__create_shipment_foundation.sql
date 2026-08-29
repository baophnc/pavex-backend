CREATE SCHEMA IF NOT EXISTS shipments;

CREATE TABLE shipments.shipments (
    id UUID PRIMARY KEY,
    tracking_code VARCHAR(32) NOT NULL,
    merchant_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    merchant_reference VARCHAR(100),

    sender_contact_name VARCHAR(120) NOT NULL,
    sender_contact_phone VARCHAR(20) NOT NULL,
    sender_address_line VARCHAR(500) NOT NULL,
    sender_province_code VARCHAR(20) NOT NULL,
    sender_province_name VARCHAR(160) NOT NULL,
    sender_ward_code VARCHAR(20) NOT NULL,
    sender_ward_name VARCHAR(160) NOT NULL,

    recipient_contact_name VARCHAR(120) NOT NULL,
    recipient_contact_phone VARCHAR(20) NOT NULL,
    recipient_address_line VARCHAR(500) NOT NULL,
    recipient_province_code VARCHAR(20) NOT NULL,
    recipient_province_name VARCHAR(160) NOT NULL,
    recipient_ward_code VARCHAR(20) NOT NULL,
    recipient_ward_name VARCHAR(160) NOT NULL,

    shipping_fee_payer VARCHAR(20) NOT NULL,

    quote_id UUID NOT NULL,
    quote_request_id UUID NOT NULL,
    rate_plan_id UUID NOT NULL,
    rate_code VARCHAR(60) NOT NULL,
    rate_revision INTEGER NOT NULL,
    rate_version VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    service_level VARCHAR(20) NOT NULL,
    pricing_zone VARCHAR(30) NOT NULL,
    base_fee BIGINT NOT NULL,
    additional_weight_fee BIGINT NOT NULL,
    area_surcharge BIGINT NOT NULL,
    cod_fee BIGINT NOT NULL,
    insurance_fee BIGINT NOT NULL,
    shipping_fee BIGINT NOT NULL,
    cod_amount BIGINT NOT NULL,
    declared_value BIGINT NOT NULL,
    estimated_min_days INTEGER NOT NULL,
    estimated_max_days INTEGER NOT NULL,
    quote_expires_at TIMESTAMPTZ NOT NULL,

    origin_service_area_id UUID NOT NULL,
    origin_region_id UUID NOT NULL,
    origin_hub_id UUID NOT NULL,
    origin_hub_code VARCHAR(60) NOT NULL,
    origin_hub_name VARCHAR(200) NOT NULL,
    origin_coverage_tier VARCHAR(20) NOT NULL,

    destination_service_area_id UUID NOT NULL,
    destination_region_id UUID NOT NULL,
    destination_hub_id UUID NOT NULL,
    destination_hub_code VARCHAR(60) NOT NULL,
    destination_hub_name VARCHAR(200) NOT NULL,
    destination_coverage_tier VARCHAR(20) NOT NULL,

    routing_status VARCHAR(20) NOT NULL,
    route_revision INTEGER NOT NULL DEFAULT 0,
    capacity_reservation_batch_id UUID,
    reserved_weight_kg NUMERIC(12,3) NOT NULL DEFAULT 0,
    route_ready_at TIMESTAMPTZ,
    estimated_delivery_at TIMESTAMPTZ,
    estimated_transit_minutes INTEGER,
    route_distance_km NUMERIC(12,3),

    status VARCHAR(30) NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMPTZ,
    note VARCHAR(1000),
    cancellation_reason VARCHAR(500),
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ux_shipment_tracking_code UNIQUE (tracking_code),
    CONSTRAINT ux_shipment_quote_id UNIQUE (quote_id),
    CONSTRAINT ux_shipment_creator_idempotency UNIQUE (created_by_user_id, idempotency_key),
    CONSTRAINT ck_shipment_tracking_code_format CHECK (
        tracking_code ~ '^PAVEX[0-9]{6}[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}$'
    ),
    CONSTRAINT ck_shipment_request_fingerprint CHECK (
        request_fingerprint ~ '^[0-9A-F]{64}$'
    ),
    CONSTRAINT ck_shipment_shipping_fee_payer CHECK (shipping_fee_payer IN ('SENDER', 'RECIPIENT')),
    CONSTRAINT ck_shipment_service_level CHECK (service_level IN ('STANDARD', 'EXPRESS')),
    CONSTRAINT ck_shipment_pricing_zone CHECK (
        pricing_zone IN ('SAME_SERVICE_AREA', 'SAME_REGION', 'CROSS_REGION')
    ),
    CONSTRAINT ck_shipment_origin_coverage_tier CHECK (
        origin_coverage_tier IN ('STANDARD', 'REMOTE', 'ISLAND')
    ),
    CONSTRAINT ck_shipment_destination_coverage_tier CHECK (
        destination_coverage_tier IN ('STANDARD', 'REMOTE', 'ISLAND')
    ),
    CONSTRAINT ck_shipment_money_non_negative CHECK (
        base_fee >= 0 AND additional_weight_fee >= 0 AND area_surcharge >= 0 AND
        cod_fee >= 0 AND insurance_fee >= 0 AND shipping_fee >= 0 AND
        cod_amount >= 0 AND declared_value >= 0
    ),
    CONSTRAINT ck_shipment_shipping_fee_snapshot CHECK (
        shipping_fee = base_fee + additional_weight_fee + area_surcharge + cod_fee + insurance_fee
    ),
    CONSTRAINT ck_shipment_eta_days CHECK (
        estimated_min_days >= 0 AND estimated_max_days >= estimated_min_days
    ),
    CONSTRAINT ck_shipment_routing_status CHECK (
        routing_status IN ('UNPLANNED', 'RESERVED', 'IN_TRANSIT', 'CONSUMED', 'RELEASED')
    ),
    CONSTRAINT ck_shipment_route_metrics CHECK (
        route_revision >= 0 AND reserved_weight_kg >= 0 AND
        (estimated_transit_minutes IS NULL OR estimated_transit_minutes >= 0) AND
        (route_distance_km IS NULL OR route_distance_km >= 0)
    ),
    CONSTRAINT ck_shipment_status CHECK (
        status IN (
            'AWAITING_PICKUP', 'PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT',
            'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED',
            'PARTIALLY_DELIVERED', 'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_shipment_lifecycle_status CHECK (
        lifecycle_status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')
    )
);

CREATE INDEX ix_shipment_status_created_at
    ON shipments.shipments(status, created_at DESC);
CREATE INDEX ix_shipment_lifecycle_created_at
    ON shipments.shipments(lifecycle_status, created_at DESC);
CREATE INDEX ix_shipment_merchant_created_at
    ON shipments.shipments(merchant_id, created_at DESC);
CREATE INDEX ix_shipment_creator_created_at
    ON shipments.shipments(created_by_user_id, created_at DESC);
CREATE INDEX ix_shipment_origin_hub_created_at
    ON shipments.shipments(origin_hub_id, created_at DESC);
CREATE INDEX ix_shipment_destination_hub_created_at
    ON shipments.shipments(destination_hub_id, created_at DESC);
CREATE INDEX ix_shipment_recipient_phone
    ON shipments.shipments(recipient_contact_phone);
CREATE INDEX ix_shipment_sender_phone
    ON shipments.shipments(sender_contact_phone);
CREATE INDEX ix_shipment_merchant_reference
    ON shipments.shipments(merchant_reference)
    WHERE merchant_reference IS NOT NULL;

CREATE TABLE shipments.parcels (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE CASCADE,
    code VARCHAR(40) NOT NULL,
    sequence INTEGER NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity INTEGER NOT NULL,
    category VARCHAR(30) NOT NULL,
    is_fragile BOOLEAN NOT NULL,
    actual_weight_kg NUMERIC(12,3) NOT NULL,
    volumetric_weight_kg NUMERIC(12,3) NOT NULL,
    chargeable_weight_kg NUMERIC(12,3) NOT NULL,
    length_cm NUMERIC(12,2) NOT NULL,
    width_cm NUMERIC(12,2) NOT NULL,
    height_cm NUMERIC(12,2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    flow_direction VARCHAR(20) NOT NULL,
    current_location_type VARCHAR(20) NOT NULL,
    current_hub_id UUID,
    current_hub_name VARCHAR(200),
    current_route_leg_id UUID,
    current_assignment_id UUID,
    custody_type VARCHAR(20) NOT NULL,
    custody_reference_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ux_shipment_parcel_code UNIQUE (code),
    CONSTRAINT ux_shipment_parcel_sequence UNIQUE (shipment_id, sequence),
    CONSTRAINT ck_shipment_parcel_sequence CHECK (sequence BETWEEN 1 AND 99),
    CONSTRAINT ck_shipment_parcel_quantity CHECK (quantity BETWEEN 1 AND 10000),
    CONSTRAINT ck_shipment_parcel_metrics CHECK (
        actual_weight_kg > 0 AND volumetric_weight_kg > 0 AND chargeable_weight_kg > 0 AND
        chargeable_weight_kg >= actual_weight_kg AND chargeable_weight_kg >= volumetric_weight_kg AND
        length_cm > 0 AND width_cm > 0 AND height_cm > 0
    ),
    CONSTRAINT ck_shipment_parcel_category CHECK (
        category IN ('DOCUMENTS', 'APPAREL', 'ELECTRONICS', 'DRY_FOOD', 'COSMETICS', 'HOUSEHOLD', 'FRAGILE_GOODS', 'OTHER')
    ),
    CONSTRAINT ck_shipment_parcel_status CHECK (
        status IN (
            'AWAITING_PICKUP', 'PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT',
            'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED',
            'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_shipment_parcel_stage CHECK (
        stage IN (
            'AWAITING_PICKUP', 'PICKED_UP', 'AT_HUB', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
            'DELIVERY_EXCEPTION', 'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_shipment_parcel_flow CHECK (flow_direction IN ('FORWARD', 'RETURN')),
    CONSTRAINT ck_shipment_parcel_location CHECK (
        current_location_type IN ('SENDER', 'HUB', 'ROUTE_LEG', 'COURIER', 'RECIPIENT', 'UNKNOWN')
    ),
    CONSTRAINT ck_shipment_parcel_custody CHECK (
        custody_type IN ('SENDER', 'HUB', 'LINE_HAUL', 'COURIER', 'RECIPIENT', 'UNKNOWN')
    )
);

CREATE INDEX ix_shipment_parcel_shipment
    ON shipments.parcels(shipment_id, sequence);
CREATE INDEX ix_shipment_parcel_stage
    ON shipments.parcels(stage, updated_at DESC);
CREATE INDEX ix_shipment_parcel_current_hub
    ON shipments.parcels(current_hub_id, updated_at DESC)
    WHERE current_hub_id IS NOT NULL;

CREATE TABLE shipments.shipment_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE CASCADE,
    parcel_id UUID REFERENCES shipments.parcels(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    code VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    actor_user_id UUID,
    hub_id UUID,
    location_name VARCHAR(200),
    data_json TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_shipment_event_status CHECK (
        status IN (
            'AWAITING_PICKUP', 'PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT',
            'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY', 'DELIVERY_FAILED',
            'PARTIALLY_DELIVERED', 'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED'
        )
    )
);

CREATE INDEX ix_shipment_event_timeline
    ON shipments.shipment_events(shipment_id, occurred_at ASC, id);
CREATE INDEX ix_shipment_event_parcel
    ON shipments.shipment_events(parcel_id, occurred_at ASC)
    WHERE parcel_id IS NOT NULL;

CREATE TABLE shipments.outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_code VARCHAR(40) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    event_version INTEGER NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_shipment_outbox_version CHECK (event_version > 0),
    CONSTRAINT ck_shipment_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_shipment_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX ix_shipment_outbox_pending
    ON shipments.outbox_events(status, next_attempt_at, occurred_at)
    WHERE status IN ('PENDING', 'FAILED');
CREATE INDEX ix_shipment_outbox_aggregate
    ON shipments.outbox_events(aggregate_id, occurred_at DESC);
