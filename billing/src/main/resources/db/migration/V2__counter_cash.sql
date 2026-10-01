-- Walk-in (counter) shipments have no merchant. Their money moves through the hub counter:
--  * a sender who pays the shipping fee hands the cash to the clerk (source COUNTER);
--  * COD the courier collects belongs to the sender (cod_payee SENDER), not to a merchant, and is
--    paid back in cash at the counter of the hub that booked the shipment;
--  * a returned shipment the recipient was meant to pay for leaves a fee the sender pays there.
ALTER TABLE billing.cash_collections
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'DELIVERY',
    ADD COLUMN cod_payee VARCHAR(20) NOT NULL DEFAULT 'MERCHANT',
    ALTER COLUMN merchant_id DROP NOT NULL,
    ALTER COLUMN delivery_attempt_id DROP NOT NULL,
    ADD CONSTRAINT ck_cash_collections_source CHECK (source IN ('DELIVERY', 'COUNTER')),
    ADD CONSTRAINT ck_cash_collections_cod_payee CHECK (cod_payee IN ('MERCHANT', 'SENDER')),
    ADD CONSTRAINT ck_cash_collections_delivery_attempt CHECK (source <> 'DELIVERY' OR delivery_attempt_id IS NOT NULL),
    ADD CONSTRAINT ck_cash_collections_merchant CHECK (cod_payee <> 'MERCHANT' OR merchant_id IS NOT NULL);

-- A shipment can have its delivery cash and its counter cash.
DROP INDEX billing.ux_cash_collections_shipment;
CREATE UNIQUE INDEX ux_cash_collections_shipment_source ON billing.cash_collections(shipment_id, source);

-- Only merchants' COD goes into merchant settlement.
DROP INDEX billing.ix_cash_collections_unsettled_cod;
CREATE INDEX ix_cash_collections_unsettled_cod
    ON billing.cash_collections(merchant_id, approved_at)
    WHERE status = 'APPROVED' AND statement_id IS NULL AND cod_amount > 0 AND cod_payee = 'MERCHANT';

-- Who sent a counter shipment and where: the counter that pays its COD back.
CREATE TABLE billing.counter_shipments (
    shipment_id UUID PRIMARY KEY,
    tracking_code VARCHAR(40) NOT NULL,
    hub_id UUID NOT NULL,
    clerk_user_id UUID NOT NULL,
    sender_name VARCHAR(120) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    shipping_fee_payer VARCHAR(20) NOT NULL,
    shipping_fee BIGINT NOT NULL,
    fee_paid_at_counter BIGINT NOT NULL,
    cod_amount BIGINT NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_counter_shipments_amounts CHECK (shipping_fee >= 0 AND fee_paid_at_counter >= 0 AND cod_amount >= 0)
);
CREATE UNIQUE INDEX ux_counter_shipments_tracking_code ON billing.counter_shipments(tracking_code);
CREATE INDEX ix_counter_shipments_sender_phone ON billing.counter_shipments(sender_phone);

-- Cash the counter owes a walk-in sender (their COD) or is owed by them (an unpaid return fee).
CREATE TABLE billing.counter_settlements (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES billing.counter_shipments(shipment_id) ON DELETE RESTRICT,
    tracking_code VARCHAR(40) NOT NULL,
    hub_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    amount BIGINT NOT NULL,
    sender_name VARCHAR(120) NOT NULL,
    sender_phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    completed_by_user_id UUID NULL,
    completed_at TIMESTAMPTZ NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_counter_settlements_kind CHECK (kind IN ('PAYOUT_COD', 'COLLECT_RETURN_FEE')),
    CONSTRAINT ck_counter_settlements_status CHECK (status IN ('PENDING', 'DONE')),
    CONSTRAINT ck_counter_settlements_amount CHECK (amount > 0),
    CONSTRAINT ck_counter_settlements_done CHECK (
        (status = 'PENDING' AND completed_at IS NULL AND completed_by_user_id IS NULL)
        OR (status = 'DONE' AND completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_counter_settlements_shipment_kind ON billing.counter_settlements(shipment_id, kind);
CREATE INDEX ix_counter_settlements_hub_status ON billing.counter_settlements(hub_id, status, created_at DESC);
CREATE INDEX ix_counter_settlements_phone ON billing.counter_settlements(sender_phone);
