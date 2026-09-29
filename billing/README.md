# Pavex Billing Service

Billing owns the money that flows after a shipment: cash couriers collect on delivery, the COD
Pavex owes merchants, the shipping fees merchants owe Pavex, and how the two are settled. It does
not own pricing (Pricing), delivery (Shipment) or workforce/hub membership (Operations).

The domain is ported from the core-api (.NET) Billing module — invoices, payments, the payOS
gateway, webhook receipts, payment reconciliation, and the cash custody chain — with money stored
as integer dong (`BIGINT` / `Long`) like the other Kotlin services.

- PostgreSQL schema `billing` in database `pavex_billing` (`billing-db`, port 5440). Service port 8088.
- Routes through the gateway: `/api/v1/billing/**`, `/api/v1/admin/billing/**`, `/api/v1/integrations/payos/**`.

## Where the data comes from

Billing subscribes to Shipment's outbox on RabbitMQ (exchange `pavex.events`, queue
`billing.shipment-events`, dead letters to `billing.shipment-events.dlq`). Each event id is recorded
in `processed_events`, and rows are unique per shipment, so redeliveries are harmless.

| Event | Effect |
| --- | --- |
| `shipment.cash-collected` | A `cash_collections` row held by the courier (COD + recipient-paid fee). |
| `shipment.closed` | A `merchant_fee_charges` row when `merchantShippingFee > 0` (sender-paid, or returned). |

## Cash custody

`HELD` by the courier → `SUBMITTED` to their hub in a remittance (declared amount must equal the
selected cash exactly) → `APPROVED` by finance. A rejected remittance leaves the cash `HELD` at the
hub, which can resubmit it after a recount. Hub access is checked against Operations
(`LAST_MILE_DELIVERY` for couriers, `CASH_HANDLING` for warehouse operators and dispatchers).

## Settlement (cấn trừ định kỳ)

`POST /api/v1/admin/billing/statements/generate` settles each merchant up to a cutoff:

1. The unsettled fee charges become one invoice, one line per shipment (a shipment is invoiced once).
2. Approved, unsettled COD is offset against that invoice as a `COD_OFFSET` payment.
3. COD left over is the statement's payout (recorded later with the bank transfer reference);
   fees left over stay on the invoice, which the merchant pays by QR or finance records in cash.

## Payments

`billing.payments.default-provider` is `fake` locally: QR codes carry a signed `PVXBILL1` payload and
an admin settles them with `POST /api/v1/admin/billing/payments/{id}/confirm`. With `payos`, payments
settle only through the signature-verified webhook, and `billing.payments.reconciliation.enabled`
polls payOS for payments whose callback never arrived.

## Service-to-service calls

Billing signs its own service tokens (`~/.pavex/keys/billing`, created by `scripts/security-bootstrap.sh`):

- Partners `GET /internal/v1/merchants/by-user/{userId}` with scope `partners.merchants.read`.
- Operations `POST /internal/v1/operations/access/check` with scope `operations.access.check`.
