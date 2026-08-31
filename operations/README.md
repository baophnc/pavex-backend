# Pavex Operations Service

`operations-service` owns the operational workforce, work shifts, assignment planning/execution and dispatch reliability boundary.
It has its own PostgreSQL database and must not read Identity, Network or Shipment tables directly.

## Patch 01 scope

This patch intentionally establishes the runtime boundary only:

- Kotlin + Spring Boot service on port `8087`
- dedicated PostgreSQL database `pavex_operations` on local port `5439`
- Flyway-owned `operations` schema
- Auth-issued user JWT validation with `roles` and `permissions`
- Eureka registration
- explicit API Gateway routes
- centralized Swagger/OpenAPI aggregation
- production profile that requires an external JWT public key

Workforce tables/endpoints arrive in Operations Patch 02. Shifts, assignments, Shipment integration and durable dispatch are intentionally not implemented in this foundation patch.

## Local endpoints

- Service: `http://localhost:8087`
- Health: `http://localhost:8087/actuator/health`
- OpenAPI through Gateway: `http://localhost:8080/openapi/operations`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- PostgreSQL: `localhost:5439`, database `pavex_operations`

Start the database from the repository root:

```bash
docker compose up -d operations-db
```

Run the service from IntelliJ or Maven:

```bash
./mvnw -pl operations -am spring-boot:run
```

The API Gateway exposes only `/api/v1/operations/**` and `/api/v1/admin/operations/**` to this service. Method-level business authorization is added with the business endpoints in later patches.
