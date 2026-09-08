ALTER TABLE shipments.parcels
    DROP CONSTRAINT ck_shipment_parcel_status,
    ADD CONSTRAINT ck_shipment_parcel_status CHECK (
        status IN (
            'AWAITING_PICKUP', 'PICKED_UP', 'AT_ORIGIN_HUB', 'IN_TRANSIT',
            'AT_TRANSIT_HUB', 'AT_DESTINATION_HUB', 'OUT_FOR_DELIVERY',
            'DELIVERY_FAILED', 'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED'
        )
    );
