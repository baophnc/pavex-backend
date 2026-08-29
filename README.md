# Pavex Backend

## Run the local stack

Requirements: Java 21, Docker Desktop, `curl`, and OpenSSL.

```bash
./scripts/dev-up.sh
```

The command generates local-only JWT/outbox secrets, starts PostgreSQL, Redis,
RabbitMQ and Mailpit, builds the reactor, then starts Eureka, Identity, Auth,
Partners, Network, Pricing and the API Gateway. Runtime logs are written to `.local/logs/`.

For an existing pre-split Auth database, `dev-up.sh` intentionally starts
Identity first and stops before Auth if the one-time RBAC cutover is still
required. Run `./scripts/cutover-auth-identity.sh --apply`, then run
`./scripts/dev-up.sh` again.

Endpoints:

- Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Identity: `http://localhost:8082`
- Partners: `http://localhost:8083`
- Network: `http://localhost:8085`
- Pricing: `http://localhost:8086`
- Eureka: `http://localhost:8761`
- Mailpit: `http://localhost:8025`
- RabbitMQ management: `http://localhost:15672` (`pavex` / `pavex`)

Stop the stack without deleting database volumes:

```bash
./scripts/dev-down.sh
```

## Final Auth / Identity split verification

After the one-time Auth -> Identity cutover and Flyway migrations are complete,
run the final smoke checks:

```bash
./scripts/smoke-auth-identity.sh
```

To include a real login and authenticated current-user/session check:

```bash
SMOKE_EMAIL='user@example.com' \
SMOKE_PASSWORD='your-password' \
./scripts/smoke-auth-identity.sh
```

## Swagger / OpenAPI

The local stack exposes one Swagger UI at the API Gateway:

```text
http://localhost:8080/swagger-ui.html
```

Use the selector in the top bar to switch between:

- `Auth API` - login, registration, password, token and session endpoints.
- `Identity API` - users, profiles, roles and permissions.
- `Partners API` - merchant, address and location APIs.
- `Network API` - logistics geography, topology, routing and capacity APIs.
- `Pricing API` - versioned rate-plan catalog APIs (quote engine arrives in Pricing Patch 02).

The aggregated OpenAPI JSON documents are also available through the Gateway:

- `http://localhost:8080/openapi/auth`
- `http://localhost:8080/openapi/identity`
- `http://localhost:8080/openapi/partners`
- `http://localhost:8080/openapi/network`
- `http://localhost:8080/openapi/pricing`

For protected operations, click **Authorize** and paste the access token returned by
`POST /api/v1/auth/login` (paste the token value only; Swagger adds `Bearer`).
`Try it out` sends public requests through the API Gateway, matching the real client path.
Internal `/internal/**` service-to-service endpoints are intentionally excluded.
Set `PAVEX_PUBLIC_BASE_URL` when Swagger should execute requests against a non-local Gateway.

Swagger/OpenAPI is disabled by default with the `prod` profile. If a production
deployment intentionally exposes documentation, enable `SPRINGDOC_API_DOCS_ENABLED`
on Auth/Identity/Partners/Network/Pricing and both `SPRINGDOC_API_DOCS_ENABLED` and
`SPRINGDOC_SWAGGER_UI_ENABLED` on the Gateway.
