-- What the courier collects from the recipient: the COD amount plus the shipping fee when the
-- recipient pays it. The price snapshot is immutable, so this is fixed when the shipment is created.
-- The collection itself is recorded once, on the first successful parcel delivery, for the full amount.
ALTER TABLE shipments.shipments
    ADD COLUMN recipient_collect_amount BIGINT NULL,
    ADD COLUMN collected_amount BIGINT NULL,
    ADD COLUMN collected_at TIMESTAMPTZ NULL,
    ADD COLUMN collected_by_user_id UUID NULL,
    ADD COLUMN collection_attempt_id UUID NULL REFERENCES shipments.delivery_attempts(id) ON DELETE RESTRICT;

UPDATE shipments.shipments
SET recipient_collect_amount = cod_amount + CASE WHEN shipping_fee_payer = 'RECIPIENT' THEN shipping_fee ELSE 0 END;

ALTER TABLE shipments.shipments
    ALTER COLUMN recipient_collect_amount SET NOT NULL,
    ADD CONSTRAINT ck_shipment_recipient_collect_amount CHECK (
        recipient_collect_amount = cod_amount + CASE WHEN shipping_fee_payer = 'RECIPIENT' THEN shipping_fee ELSE 0 END
    ),
    ADD CONSTRAINT ck_shipment_collection CHECK (
        (collected_amount IS NULL AND collected_at IS NULL AND collected_by_user_id IS NULL AND collection_attempt_id IS NULL)
        OR (
            collected_amount = recipient_collect_amount AND recipient_collect_amount > 0 AND
            collected_at IS NOT NULL AND collected_by_user_id IS NOT NULL AND collection_attempt_id IS NOT NULL
        )
    );
