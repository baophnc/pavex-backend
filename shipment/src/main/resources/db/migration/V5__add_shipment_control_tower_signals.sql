CREATE TABLE shipments.shipment_control_signals (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments.shipments(id) ON DELETE CASCADE,
    parcel_id UUID NULL REFERENCES shipments.parcels(id) ON DELETE CASCADE,
    signal_type VARCHAR(40) NOT NULL,
    code VARCHAR(80) NOT NULL,
    hub_id UUID NOT NULL,
    route_leg_id UUID NULL,
    actor_user_id UUID NULL,
    description VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_shipment_control_signal_type CHECK (signal_type IN ('ROUTE_DEVIATION'))
);

CREATE INDEX ix_shipment_control_signals_shipment_time
    ON shipments.shipment_control_signals(shipment_id, occurred_at DESC);
CREATE INDEX ix_shipment_control_signals_hub_time
    ON shipments.shipment_control_signals(hub_id, occurred_at DESC);
