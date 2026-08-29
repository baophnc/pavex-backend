# Pavex Pricing Service

Pricing owns versioned rate plans and public shipping quotes. It does not own Network topology,
Shipment routing, capacity reservations, invoicing or payment collection.

## Patch 01 foundation

- PostgreSQL schema `pricing` in database `pavex_pricing`.
- `RatePlan` lifecycle model (`DRAFT`, `ACTIVE`, `RETIRED`).
- Six typed rate-rule slots built from `STANDARD|EXPRESS` and
  `SAME_SERVICE_AREA|SAME_REGION|CROSS_REGION`.
- Draft-only editing. Active and retired plans are immutable.
- VND amounts are stored as integer dong (`BIGINT` / Kotlin `Long`).
- Weight/dimension/rate math uses `BigDecimal`-backed numeric columns.
- Local-only demo catalog when `pricing.seed.demo.enabled=true`.

## Patch 02 quote engine

`POST /api/v1/pricing/quotes` is public and keeps compatibility with the existing customer portal.
A request may send the legacy top-level parcel fields or a `parcels` array; a non-empty array wins.
The response still exposes `Local|Regional|National` and `Standard|Express`, while Pricing stores the
clearer internal `SAME_SERVICE_AREA|SAME_REGION|CROSS_REGION` model.

Quote creation:

1. Selects the one active `RatePlan` for the current time.
2. Validates 1-20 parcels, COD, declared value and configured weight/dimension limits.
3. Resolves both locations through Network over an authenticated internal service call.
4. Computes actual, volumetric and chargeable weight. Chargeable weight rounds each parcel up to 0.5 kg.
5. Calculates Standard and Express options from the active rate rules.
6. Applies remote/island, COD and insurance charges, then rounds the final VND total up to 1,000 dong.
7. Persists one normalized quote request, its parcels once, and two quote options.

Pricing deliberately does not ask Network for a route or live capacity during quoting. Route planning
and capacity reservation remain Shipment concerns so public quote availability does not flap with a
single departure's capacity.

### Internal Network contract

`platform-contracts` now carries the narrow location-resolution wire contract. Pricing signs a
short-lived RSA service JWT with:

- `sub=pricing-service`
- `aud=network-service`
- `scope=network.pricing.resolve`

Network trusts only Pricing's public key on `/internal/v1/network/**`. Run `./scripts/dev-setup.sh`
after applying Patch 02 so `pricing/.keys/private.pem` and `pricing/.keys/public.pem` exist locally.
Never commit these keys.

The demo values are development data, not a production tariff. Patch 03 adds the complete
activation/retirement workflow, Shipment-facing quote contract, audit and production hardening.

## Local stack

The easiest way to exercise quote creation is the full local stack because Network also validates
locations against Partners:

```bash
./scripts/dev-up.sh
```

Then run:

```bash
./scripts/smoke-pricing-foundation.sh
./scripts/smoke-pricing-quotes.sh
```

Service: `http://localhost:8086`

Gateway API: `http://localhost:8080/api/v1/pricing/quotes`

Gateway OpenAPI: `http://localhost:8080/openapi/pricing`

## Patch 03 lifecycle, audit and Shipment contract

Patch 03 closes the Pricing backend before Shipment work begins:

- Draft plans can be cloned into the next revision without changing active history.
- Activation requires all six Standard/Express x pricing-zone rules and rejects overlapping active windows.
- Active plans can be retired; active/retired plans remain immutable.
- Every admin create/update/rule/lifecycle/delete mutation is written to `pricing.audit_logs` with actor, before/after JSON and optional reason.
- `GET /api/v1/admin/pricing/audit` exposes paged audit history for Management.
- `PricingQuoteSnapshot` in `platform-contracts` is the immutable Shipment-facing quote contract, including resolved locations, parcel metrics, price breakdown, service level and expiry.
- `GET /api/v1/admin/pricing/quotes/{quoteId}` lets support/admin inspect the same snapshot even after expiry.
- `GET /internal/v1/pricing/quotes/{quoteId}` is reserved for Shipment and rejects expired quotes.

The Shipment endpoint is disabled until Shipment exists. Later enable it with
`PRICING_SHIPMENT_INTEGRATION_ENABLED=true` and provide Shipment's RSA public key via
`SHIPMENT_SERVICE_JWT_PUBLIC_KEY`. The trusted token must use `sub=shipment-service`,
`aud=pricing-service`, `typ=service` and `scope=pricing.quote.read`. This keeps Pricing runnable today
without creating or sharing Shipment keys prematurely.

Smoke the Patch 03 storage invariants after Pricing starts:

```bash
./scripts/smoke-pricing-hardening.sh
```
