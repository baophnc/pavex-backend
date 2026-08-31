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

The original C# scan handler also checks Operations hub assignments. The current Kotlin repository has no Operations service/module in the Maven reactor, so Patch 04 intentionally stops at `scans.create` authorization plus Network hub/route validation instead of inventing an unavailable cross-service contract.

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
- service-to-service JWT integration with Pricing, Partners and Network,
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
docker compose up -d shipment-db
JWT_PUBLIC_KEY="file:$PWD/auth/.keys/public.pem" \
SHIPMENT_SERVICE_JWT_PRIVATE_KEY="file:$PWD/auth/.keys/private.pem" \
SHIPMENT_SERVICE_JWT_PUBLIC_KEY="file:$PWD/auth/.keys/public.pem" \
./mvnw -pl shipment spring-boot:run
```

Patch 02 requires Discovery, Partners, Network and Pricing to be running and registered in Eureka.

Service: `http://localhost:8084`
Database: `jdbc:postgresql://localhost:5438/pavex_shipments`

Smoke database foundation after the application has run Flyway:

```bash
./scripts/smoke-shipment-foundation.sh
```

## Patch 05 - delivery attempts and proof

- `POST /api/v1/deliveries/attempts` requires `deliveries.complete-own`.
- The caller must be the courier currently holding the parcel (`custody_type=COURIER` and `custody_reference_id` equals the JWT subject), which provides real ownership enforcement while the Kotlin reactor still has no Operations service.
- Successful delivery requires a proof method (`OTP`, `SIGNATURE`, or `PHOTO`), recipient name, and evidence reference.
- Failed attempts require a failure reason and keep the parcel eligible for a later `OUT_FOR_DELIVERY` retry or return flow.
- Attempts are durable, numbered per parcel, idempotent per parcel/key, and emit `shipment.delivery-attempt-recorded` through the Shipment outbox.
- Terminal `DELIVERED`/`RETURNED` shipment states now close the shipment lifecycle and consume the reserved route locally. The original C# solution performs that closure through Operations integration; this local completion is the Kotlin fallback until Operations exists in the Maven reactor.

## Admin shipment query contract

Management Portal uses read-only admin endpoints guarded by `shipments.admin.read`:

- `GET /api/v1/admin/shipments` supports `status`, `lifecycleStatus`, `merchantId`, `q`, `cursor`, and `limit`.
- `GET /api/v1/admin/shipments/{trackingCode}` returns internal ownership/routing identifiers, the customer detail snapshot, the complete event stream, and delivery attempts.

The admin list returns `totalCount` in addition to cursor pagination so dashboards can render operational counters without using merchant-owned APIs.
