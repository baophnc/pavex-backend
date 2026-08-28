# Pavex Partners Service

`partners-service` owns merchant, customer-address, pickup-address and Vietnam
administrative-location data. It is a separate microservice and database; it
must not read Auth or Identity tables directly.

## Local endpoints

- Service: `http://localhost:8083`
- Health: `http://localhost:8083/actuator/health`
- OpenAPI through Gateway: `http://localhost:8080/openapi/partners`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- PostgreSQL: `localhost:5435`, database `pavex_partners`

## Patch 01 boundary

This foundation patch provides runtime wiring only:

- Spring Boot/Kotlin service on port `8083`
- dedicated PostgreSQL database/schema
- Flyway ownership boundary
- Eureka registration
- Auth-issued user JWT validation
- explicit API Gateway route ownership
- centralized Swagger/OpenAPI aggregation

Business tables and endpoints intentionally start in later patches. Patch 02
adds Partners -> Identity service authentication and merchant eligibility;
Patch 03 starts locations and addresses.
