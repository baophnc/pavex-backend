# Pavex Shipment Service

Shipment owns the durable shipment aggregate after a customer selects a Pricing quote. The service keeps internal UUIDs for relational integrity while exposing a stable PAVEX shipment code as the human-facing identifier.

## Patch 04 boundary

Patch 01 established the aggregate and persistence foundation. Patch 02 added the first end-to-end write path: `POST /api/v1/shipments`. Patch 03 added owned shipment queries, pre-pickup cancellation, and public tracking. Patch 04 adds the first operational movement slice: authenticated parcel scans backed by Network hub/route validation and parcel/shipment status transitions.

Patch 03 adds:

- `GET /api/v1/shipments` with status filtering and opaque keyset cursor pagination,
- `GET /api/v1/shipments/{trackingCode}` scoped to the authenticated creator,
- `POST /api/v1/shipments/{trackingCode}/cancel` with `shipments.cancel-own`,
- `GET /api/v1/tracking/{code}` for public shipment or parcel-code tracking,
- customer-visible timeline filtering,
- cancellation outbox emission and retry-safe Network capacity release,
- public API contracts that keep internal UUIDs out of normal UI responses.

Patch 04 adds:

- `POST /api/v1/parcel-scans` with `scans.create`,
- active-hub validation through Shipment -> Network service JWT,
- reserved-route membership validation for in-transit/return scans,
- the v1 parcel status transition graph from the original Shipments module,
- automatic aggregate `ShipmentStatus` recalculation including partial delivery,
- customer-visible shipment timeline events for accepted scans,
- a `shipment.parcel-scanned` outbox event for downstream consumers,
- optimistic-lock conflict handling for concurrent scanner activity.

Operations Patch 05 closes the original scan-authorization gap: Shipment now asks Operations for live hub capability and assignment ownership/readiness before operational parcel movements. Warehouse scans require an active Operations hub membership; pickup, line-haul and last-mile movements require the matching in-progress assignment.

The service now contains:

- shipment code + parcel code strategy (`PAVEXyyMMddXXXXXXXXXX`, `...-01`),
- sender/recipient snapshots including province/ward **names** as well as catalog codes,
- immutable pricing snapshot fields using integer VND amounts,
- origin/destination Network snapshots including hub **names**; there is no legacy gateway-parent snapshot,
- parcel content/weight snapshots and physical-state fields,
- shipment event timeline storage,
- optimistic locking,
- quote single-use and creator/idempotency unique constraints,
- durable shipment outbox storage for later Operations/Billing/Notification consumers,
- idempotent shipment creation using `Idempotency-Key` and SHA-256 request fingerprints,
- service-to-service JWT integration with Pricing, Partners, Network and Operations,
- quote single-use enforcement and network-capacity compensation on failed inserts.

## Human-facing identifiers

Public/customer/admin UI should use:

- shipment: `trackingCode`,
- parcel: `code`,
- hub: `hubName`,
- province/ward: snapshot names.

UUIDs remain internal transport/database identifiers and should not be rendered as normal list/filter labels.

## Local runtime

```bash
bash scripts/security-bootstrap-local.sh
docker compose up -d shipment-db
./mvnw -pl shipment spring-boot:run
```

Local defaults verify user tokens with `.local/keys/auth/public.pem` and sign Shipment
service tokens with the separate `.local/keys/shipment/` keypair.

Patch 02 requires Discovery, Partners, Network and Pricing to be running and registered in Eureka.

Service: `http://localhost:8084`
Database: `jdbc:postgresql://localhost:5438/pavex_shipments`

Smoke database foundation after the application has run Flyway:

```bash
./scripts/smoke-shipment-foundation.sh
```

## Patch 05 - delivery attempts and proof

- `POST /api/v1/deliveries/attempts` requires `deliveries.complete-own` and an `assignmentId`.
- The caller must both own an in-progress Operations `LAST_MILE_DELIVERY` assignment and currently hold the parcel (`custody_type=COURIER`, `custody_reference_id` equals the JWT subject).
- Successful delivery requires a proof method (`OTP`, `SIGNATURE`, or `PHOTO`), recipient name, and evidence reference.
- Failed attempts require a failure reason and keep the parcel eligible for a later `OUT_FOR_DELIVERY` retry or return flow.
- Attempts are durable, numbered per parcel, idempotent per parcel/key, and emit `shipment.delivery-attempt-recorded` through the Shipment outbox.
- Terminal `DELIVERED`/`RETURNED` shipment states close the shipment lifecycle locally, while Operations Patch 05 completes the owned last-mile assignment when all parcels are delivered.

## Admin shipment query contract

Management Portal uses read-only admin endpoints guarded by `shipments.admin.read`:

- `GET /api/v1/admin/shipments` supports `status`, `lifecycleStatus`, `merchantId`, `q`, `cursor`, and `limit`.
- `GET /api/v1/admin/shipments/{trackingCode}` returns internal ownership/routing identifiers, the customer detail snapshot, the complete event stream, and delivery attempts.

The admin list returns `totalCount` in addition to cursor pagination so dashboards can render operational counters without using merchant-owned APIs.

## Operations Patch 05 integration

Shipment creation now requires Operations in addition to Partners, Pricing and Network. Network returns the exact reserved route legs, Shipment sends those legs to Operations, and Operations creates pickup/origin-handling/line-haul/destination-handling/last-mile assignments idempotently.

`POST /api/v1/parcel-scans` accepts optional `assignmentId` and `routeLegId`. They become mandatory for assignment-driven transitions such as pickup, line-haul departure and last-mile dispatch. `DELIVERED`/`DELIVERY_FAILED` can no longer be forged through the generic scan endpoint; use the delivery-attempt API so proof and last-mile ownership are enforced.
