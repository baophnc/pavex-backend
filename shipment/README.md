# Pavex Shipment Service

Shipment owns the durable shipment aggregate after a customer selects a Pricing quote. The service keeps internal UUIDs for relational integrity while exposing a stable PAVEX shipment code as the human-facing identifier.

## Patch 01 boundary

This checkpoint establishes the service and persistence model only. It intentionally does not create shipments yet; Pricing/Partners/Network integration arrives in Shipment Patch 02.

The foundation contains:

- shipment code + parcel code strategy (`PAVEXyyMMddXXXXXXXXXX`, `...-01`),
- sender/recipient snapshots including province/ward **names** as well as catalog codes,
- immutable pricing snapshot fields using integer VND amounts,
- origin/destination Network snapshots including hub **names**; there is no legacy gateway-parent snapshot,
- parcel content/weight snapshots and physical-state fields,
- shipment event timeline storage,
- optimistic locking,
- quote single-use and creator/idempotency unique constraints,
- durable shipment outbox storage for later Operations/Billing/Notification consumers.

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
EUREKA_ENABLED=false \
./mvnw -pl shipment spring-boot:run
```

Service: `http://localhost:8084`
Database: `jdbc:postgresql://localhost:5438/pavex_shipments`

Smoke database foundation after the application has run Flyway:

```bash
./scripts/smoke-shipment-foundation.sh
```
