# Pavex Pricing Service

Pricing owns versioned rate plans and rate rules. It does not own Network topology,
Shipment routing, capacity reservations, invoicing or payment collection.

Patch 01 establishes only the rate catalog foundation:

- PostgreSQL schema `pricing` in database `pavex_pricing`.
- `RatePlan` lifecycle model (`DRAFT`, `ACTIVE`, `RETIRED`).
- Six typed rate-rule slots built from `STANDARD|EXPRESS` and
  `SAME_SERVICE_AREA|SAME_REGION|CROSS_REGION`.
- Draft-only editing. Active and retired plans are immutable.
- VND amounts are stored as integer dong (`BIGINT` / Kotlin `Long`).
- Weight/dimension/rate math uses `BigDecimal`-backed numeric columns.
- Local-only demo catalog when `pricing.seed.demo.enabled=true`.

The demo values are development data, not a production tariff. Patch 02 will add
Network location resolution and the public quote engine. Patch 03 will add the
activation/retirement workflow, Shipment-facing quote contract, audit and
hardening.

## Local run

Start the database:

```bash
docker compose up -d pricing-db
```

Run the service from the repository root so the default JWT key path resolves:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw -pl pricing spring-boot:run
```

If Maven changes the working directory on your machine, use an absolute key path:

```bash
SPRING_PROFILES_ACTIVE=local \
JWT_PUBLIC_KEY="file:$PWD/auth/.keys/public.pem" \
./mvnw -pl pricing spring-boot:run
```

Service: `http://localhost:8086`

Gateway OpenAPI: `http://localhost:8080/openapi/pricing`
