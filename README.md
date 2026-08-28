# Pavex Backend

## Run the local stack

Requirements: Java 21, Docker Desktop, `curl`, and OpenSSL.

```bash
./scripts/dev-up.sh
```

The command generates local-only JWT/outbox secrets, starts PostgreSQL, Redis,
RabbitMQ and Mailpit, builds the reactor, then starts Eureka, Auth, Identity and
the API Gateway. Runtime logs are written to `.local/logs/`.

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
