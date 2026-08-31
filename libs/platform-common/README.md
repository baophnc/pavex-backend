# Pavex Platform Common

`platform-common` is a plain Kotlin/JVM library for technical primitives that are genuinely shared across Pavex backend modules.

It is **not** a Spring Boot application and must not contain controllers, services, repositories, JPA entities, database migrations, or domain-specific business rules.

## Good candidates

- HTTP header names used across services.
- Pagination response shape.
- JWT claim/token-type constants shared by resource servers and token issuers.
- Small framework-independent technical primitives.

## Keep out of this module

- `AccountStatus`, `ProfileStatus`, Identity roles, or other Identity-owned domain models.
- `MerchantStatus` or Partners business models.
- `ShipmentStatus` or shipment business models.
- JPA entities and repositories.
- Spring configuration and business services.

A type belongs here only when at least two modules need the exact same technical meaning.

## Current shared primitives

- `PageResponse<T>` for compatible page-shaped APIs.
- `OneBasedPagination` for the common API `page` / `pageSize` normalization rule.
- `ApiErrorResponse` for services using the `{code, message}` error envelope.
- `PavexHeaders`, `PavexJwtClaims`, and `PavexTokenTypes` for shared protocol names.
