# Pavex API Gateway

Public edge gateway for Pavex microservices.

## Responsibilities in this checkpoint

- Public entry point on port `8080`.
- Resolve `lb://auth-service` and `lb://identity-service` through Eureka.
- Expose only explicit Auth and Identity API routes.
- Redis-backed edge rate limiting by remote IP.
- Global CORS for the frontend origin.
- Generate/propagate `X-Request-Id`.
- Health endpoint via Spring Boot Actuator.

Authentication/authorization remains enforced by Auth and each resource service in this
checkpoint. The gateway is the public ingress boundary, but downstream
services still own authorization and must not trust arbitrary internal
traffic. Service-to-service authentication is a later platform checkpoint.

## Local dependencies

Start PostgreSQL and Redis from the repository root:

```bash
docker compose up -d auth-db identity-db gateway-redis
```

Start Eureka first:

```bash
./mvnw -pl platform/discovery-server spring-boot:run
```

Start Auth and Identity in separate terminals / IntelliJ, then start the gateway:

```bash
./mvnw -pl platform/api-gateway spring-boot:run
```

Endpoints:

- Gateway: `http://localhost:8080`
- Gateway health: `http://localhost:8080/actuator/health`
- Eureka: `http://localhost:8761`
- Auth direct dev port: `http://localhost:8081`
- Identity direct dev port: `http://localhost:8082`

Frontend traffic should move to the gateway URL after this checkpoint.

## Local environment overrides

```text
API_GATEWAY_PORT=8080
EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka/
REDIS_HOST=localhost
REDIS_PORT=6379
GATEWAY_ALLOWED_ORIGIN=http://localhost:3000
```

The IP key resolver intentionally uses the actual socket remote address and
ignores arbitrary client-supplied `X-Forwarded-For`. When the gateway is put
behind a trusted reverse proxy/load balancer, configure trusted proxy handling
as part of deployment hardening instead of trusting all forwarded headers.
