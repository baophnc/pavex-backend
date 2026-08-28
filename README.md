# Pavex Backend

## Run the local stack

Requirements: Java 21, Docker Desktop, `curl`, and OpenSSL.

```bash
./scripts/dev-up.sh
```

The command generates local-only JWT/outbox secrets, starts PostgreSQL, Redis,
RabbitMQ and Mailpit, builds the reactor, then starts Eureka, Identity, Auth and
the API Gateway. Runtime logs are written to `.local/logs/`.

For an existing pre-split Auth database, `dev-up.sh` intentionally starts
Identity first and stops before Auth if the one-time RBAC cutover is still
required. Run `./scripts/cutover-auth-identity.sh --apply`, then run
`./scripts/dev-up.sh` again.

Endpoints:

- Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Identity: `http://localhost:8082`
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
