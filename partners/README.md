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

## Service-to-service boundary

Partners owns a dedicated RSA key pair under `partners/.keys/` for local
development. Calls to Identity use short-lived service JWTs:

- issuer: `http://localhost:8083`
- subject: `partners-service`
- audience: `identity-service`
- token type: `service`
- scope: `identity.merchant`

The scope is deliberately narrow. Identity accepts it only for merchant
eligibility and keeps Auth's broader `identity.internal` boundary separate.

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
adds Partners -> Identity service authentication and canonical merchant
eligibility without creating any Partners business tables. Patch 03 starts
locations and addresses.
