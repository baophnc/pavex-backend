-- Billing ledger. Ported from the core-api (.NET) Billing module; money is whole VND (BIGINT)
-- like the rest of the Kotlin services instead of numeric(18,2).
CREATE SCHEMA IF NOT EXISTS billing;

-- Invoices a merchant pays: the shipping fees of a settlement period, less the COD offset.
CREATE TABLE billing.customer_invoices (
    id UUID PRIMARY KEY,
    invoice_number VARCHAR(50) NOT NULL,
    merchant_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_amount BIGINT NOT NULL,
    paid_amount BIGINT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    paid_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    cancelled_by_user_id UUID NULL,
    cancellation_reason VARCHAR(500) NULL,
    note VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_customer_invoices_amounts CHECK (total_amount > 0 AND paid_amount >= 0 AND paid_amount <= total_amount),
    CONSTRAINT ck_customer_invoices_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_customer_invoices_status CHECK (status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    CONSTRAINT ck_customer_invoices_due CHECK (due_at >= issued_at)
);
CREATE UNIQUE INDEX ux_customer_invoices_invoice_number ON billing.customer_invoices(invoice_number);
CREATE INDEX ix_customer_invoices_merchant_status_issued ON billing.customer_invoices(merchant_id, status, issued_at DESC);

CREATE TABLE billing.customer_invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES billing.customer_invoices(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    shipment_id UUID NOT NULL,
    tracking_code VARCHAR(40) NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_invoice_lines_amount CHECK (amount > 0),
    CONSTRAINT ck_customer_invoice_lines_sequence CHECK (sequence > 0)
);
CREATE UNIQUE INDEX ux_customer_invoice_lines_invoice_sequence ON billing.customer_invoice_lines(invoice_id, sequence);
-- A shipment fee is invoiced at most once.
CREATE UNIQUE INDEX ux_customer_invoice_lines_shipment ON billing.customer_invoice_lines(shipment_id);

CREATE TABLE billing.invoice_payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES billing.customer_invoices(id) ON DELETE CASCADE,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    reference VARCHAR(100) NOT NULL,
    provider VARCHAR(30) NULL,
    provider_payment_id VARCHAR(200) NULL,
    provider_reference VARCHAR(200) NULL,
    checkout_url VARCHAR(2048) NULL,
    qr_payload VARCHAR(2000) NULL,
    qr_expires_at TIMESTAMPTZ NULL,
    collected_by_user_id UUID NULL,
    completed_at TIMESTAMPTZ NULL,
    cancelled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_invoice_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_invoice_payments_method CHECK (method IN ('CASH', 'QR', 'COD_OFFSET')),
    CONSTRAINT ck_invoice_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED', 'EXPIRED'))
);
CREATE UNIQUE INDEX ux_invoice_payments_reference ON billing.invoice_payments(reference);
CREATE UNIQUE INDEX ux_invoice_payments_provider_payment
    ON billing.invoice_payments(provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
CREATE INDEX ix_invoice_payments_invoice_status ON billing.invoice_payments(invoice_id, status, created_at);
CREATE INDEX ix_invoice_payments_open_qr
    ON billing.invoice_payments(updated_at)
    WHERE method = 'QR' AND status IN ('PENDING', 'EXPIRED');

-- Verified payment-provider callbacks, kept for idempotency and audit.
CREATE TABLE billing.payment_webhook_receipts (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    external_event_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    payment_id UUID NULL REFERENCES billing.invoice_payments(id) ON DELETE SET NULL,
    status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL,
    failure_reason VARCHAR(1000) NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_payment_webhook_receipts_status CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);
CREATE UNIQUE INDEX ux_payment_webhook_receipts_event ON billing.payment_webhook_receipts(provider, external_event_id);
CREATE INDEX ix_payment_webhook_receipts_status ON billing.payment_webhook_receipts(status, received_at);

-- Settlement periods: COD collected for a merchant is offset against the shipping fees it owes.
CREATE TABLE billing.settlement_statements (
    id UUID PRIMARY KEY,
    statement_number VARCHAR(40) NOT NULL,
    merchant_id UUID NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    cod_total BIGINT NOT NULL,
    fee_total BIGINT NOT NULL,
    offset_amount BIGINT NOT NULL,
    payout_amount BIGINT NOT NULL,
    invoice_id UUID NULL REFERENCES billing.customer_invoices(id) ON DELETE RESTRICT,
    payout_status VARCHAR(20) NOT NULL,
    payout_reference VARCHAR(100) NULL,
    payout_bank_name VARCHAR(100) NULL,
    payout_account_number VARCHAR(40) NULL,
    payout_account_name VARCHAR(200) NULL,
    paid_at TIMESTAMPTZ NULL,
    paid_by_user_id UUID NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_settlement_statements_amounts CHECK (
        cod_total >= 0 AND fee_total >= 0 AND offset_amount >= 0 AND payout_amount >= 0 AND
        offset_amount = LEAST(cod_total, fee_total) AND payout_amount = cod_total - offset_amount
    ),
    CONSTRAINT ck_settlement_statements_invoice CHECK ((fee_total > 0) = (invoice_id IS NOT NULL)),
    CONSTRAINT ck_settlement_statements_payout_status CHECK (payout_status IN ('NOT_REQUIRED', 'PENDING', 'PAID')),
    CONSTRAINT ck_settlement_statements_payout CHECK (
        (payout_amount = 0 AND payout_status = 'NOT_REQUIRED') OR
        (payout_amount > 0 AND payout_status = 'PENDING' AND paid_at IS NULL) OR
        (payout_amount > 0 AND payout_status = 'PAID' AND paid_at IS NOT NULL AND payout_reference IS NOT NULL)
    )
);
CREATE UNIQUE INDEX ux_settlement_statements_number ON billing.settlement_statements(statement_number);
CREATE INDEX ix_settlement_statements_merchant ON billing.settlement_statements(merchant_id, created_at DESC);

-- Shipping fees charged to merchants, recorded from shipment.closed events.
CREATE TABLE billing.merchant_fee_charges (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL,
    tracking_code VARCHAR(40) NOT NULL,
    merchant_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    shipping_fee_payer VARCHAR(20) NOT NULL,
    final_status VARCHAR(30) NOT NULL,
    closed_at TIMESTAMPTZ NOT NULL,
    statement_id UUID NULL REFERENCES billing.settlement_statements(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_merchant_fee_charges_amount CHECK (amount > 0)
);
CREATE UNIQUE INDEX ux_merchant_fee_charges_shipment ON billing.merchant_fee_charges(shipment_id);
CREATE INDEX ix_merchant_fee_charges_unsettled ON billing.merchant_fee_charges(merchant_id, closed_at) WHERE statement_id IS NULL;

-- A courier hands collected cash to a hub in a remittance; finance approves or rejects it.
CREATE TABLE billing.cash_remittances (
    id UUID PRIMARY KEY,
    remittance_number VARCHAR(60) NOT NULL,
    courier_user_id UUID NOT NULL,
    hub_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    expected_amount BIGINT NOT NULL,
    declared_amount BIGINT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ NULL,
    approved_by_user_id UUID NULL,
    rejected_at TIMESTAMPTZ NULL,
    rejected_by_user_id UUID NULL,
    rejection_reason VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_cash_remittances_amounts CHECK (expected_amount > 0 AND declared_amount = expected_amount),
    CONSTRAINT ck_cash_remittances_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED'))
);
CREATE UNIQUE INDEX ux_cash_remittances_number ON billing.cash_remittances(remittance_number);
CREATE INDEX ix_cash_remittances_hub_status ON billing.cash_remittances(hub_id, status, submitted_at DESC);
CREATE INDEX ix_cash_remittances_courier ON billing.cash_remittances(courier_user_id, submitted_at DESC);

-- Money a courier collected from a recipient on delivery (COD plus any recipient-paid fee).
CREATE TABLE billing.cash_collections (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL,
    tracking_code VARCHAR(40) NOT NULL,
    merchant_id UUID NOT NULL,
    delivery_attempt_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    cod_amount BIGINT NOT NULL,
    fee_amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reference VARCHAR(100) NOT NULL,
    collected_by_user_id UUID NOT NULL,
    origin_hub_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_custodian_user_id UUID NULL,
    current_hub_id UUID NULL,
    current_remittance_id UUID NULL REFERENCES billing.cash_remittances(id) ON DELETE RESTRICT,
    collected_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NULL,
    approved_at TIMESTAMPTZ NULL,
    approved_by_user_id UUID NULL,
    last_rejected_at TIMESTAMPTZ NULL,
    last_rejected_by_user_id UUID NULL,
    last_rejection_reason VARCHAR(500) NULL,
    statement_id UUID NULL REFERENCES billing.settlement_statements(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_cash_collections_amounts CHECK (
        amount > 0 AND cod_amount >= 0 AND fee_amount >= 0 AND amount = cod_amount + fee_amount
    ),
    CONSTRAINT ck_cash_collections_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_cash_collections_status CHECK (status IN ('HELD', 'SUBMITTED', 'APPROVED')),
    CONSTRAINT ck_cash_collections_statement CHECK (statement_id IS NULL OR status = 'APPROVED')
);
CREATE UNIQUE INDEX ux_cash_collections_shipment ON billing.cash_collections(shipment_id);
CREATE UNIQUE INDEX ux_cash_collections_reference ON billing.cash_collections(reference);
CREATE INDEX ix_cash_collections_custodian ON billing.cash_collections(current_custodian_user_id, status);
CREATE INDEX ix_cash_collections_hub ON billing.cash_collections(current_hub_id, status, updated_at DESC);
CREATE INDEX ix_cash_collections_unsettled_cod
    ON billing.cash_collections(merchant_id, approved_at)
    WHERE status = 'APPROVED' AND statement_id IS NULL AND cod_amount > 0;

CREATE TABLE billing.cash_remittance_items (
    id UUID PRIMARY KEY,
    remittance_id UUID NOT NULL REFERENCES billing.cash_remittances(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    collection_id UUID NOT NULL REFERENCES billing.cash_collections(id) ON DELETE RESTRICT,
    amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_cash_remittance_items_amount CHECK (amount > 0)
);
CREATE UNIQUE INDEX ux_cash_remittance_items_sequence ON billing.cash_remittance_items(remittance_id, sequence);
CREATE INDEX ix_cash_remittance_items_collection ON billing.cash_remittance_items(collection_id);

-- Where Pavex transfers a merchant's payout.
CREATE TABLE billing.merchant_payout_accounts (
    merchant_id UUID PRIMARY KEY,
    bank_name VARCHAR(100) NOT NULL,
    account_number VARCHAR(40) NOT NULL,
    account_name VARCHAR(200) NOT NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

-- Shipment events already applied; RabbitMQ delivers at least once.
CREATE TABLE billing.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
