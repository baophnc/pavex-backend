# Pavex Shipment Service

Shipment owns the durable shipment aggregate after a customer selects a Pricing quote. The service keeps internal UUIDs for relational integrity while exposing a stable PAVEX shipment code as the human-facing identifier.

## Patch 03 boundary

Patch 01 established the aggregate and persistence foundation. Patch 02 added the first end-to-end write path: `POST /api/v1/shipments`. Patch 03 completes the first customer-facing lifecycle slice with owned shipment queries, pre-pickup cancellation, and public shipment/parcel tracking.

Patch 03 adds:

- `GET /api/v1/shipments` with status filtering and opaque keyset cursor pagination,
- `GET /api/v1/shipments/{trackingCode}` scoped to the authenticated creator,
- `POST /api/v1/shipments/{trackingCode}/cancel` with `shipments.cancel-own`,
- `GET /api/v1/tracking/{code}` for public shipment or parcel-code tracking,
- customer-visible timeline filtering,
- cancellation outbox emission and retry-safe Network capacity release,
- public API contracts that keep internal UUIDs out of normal UI responses.

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
