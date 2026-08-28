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
eligibility and idempotent `MERCHANT` role synchronization; Auth's broader
`identity.internal` boundary remains separate. Role changes are written to a
Partners outbox in the same transaction as merchant status changes and retried
until Identity accepts them.

Shipment calls the internal order-capability endpoint with its own service JWT:

- subject: `shipment-service`
- audience: `partners-service`
- token type: `service`
- scope: `partners.orders.evaluate`

Production must provide `SHIPMENT_SERVICE_JWT_ISSUER` and
`SHIPMENT_SERVICE_JWT_PUBLIC_KEY`.

## API surface

- Public location catalog: `/api/v1/locations/**`
- Personal addresses: `/api/v1/partners/me/addresses/**`
- Merchant profile: `/api/v1/merchants/me/profile/**`
- Merchant pickup addresses: `/api/v1/merchants/me/pickup-addresses/**`
- Merchant administration: `/api/v1/admin/merchants/**`
- Shipment capability contract:
  `/internal/v1/merchants/by-user/{userId}/order-capability`

Merchant application eligibility is sourced from Identity. A merchant can
create an order only when the Identity account remains eligible, the merchant
is `ACTIVE`, and a default pickup address exists. Shipment receives restriction
codes instead of reconstructing those rules from JWT roles.

## Implemented boundary

The service currently owns:

- the current Vietnam province/ward dataset;
- user-address CRUD with a single-default invariant;
- merchant application, update and rejected-profile resubmission;
- business-facing merchant codes (`PVM000001`, ...);
- pickup-address CRUD with a single-default invariant;
- admin search, approval, rejection, suspension and reactivation;
- merchant status history and durable Identity role synchronization;
- the canonical `canCreateOrder` decision used by Shipment.

Staff/employees, multiple owners, branches, bank accounts, wallets,
commissions, billing, contracts, business document upload and merchant API keys
remain outside this MVP.
