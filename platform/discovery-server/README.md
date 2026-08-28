# Pavex Discovery Server

Local Eureka service registry for PAVEX microservices.

## Run

From repository root:

```bash
./mvnw -pl platform/discovery-server spring-boot:run
```

Dashboard: `http://localhost:8761`

Then start `auth-service` and `identity-service`. They should appear in Eureka as
`AUTH-SERVICE` and `IDENTITY-SERVICE`, both with status `UP`.

Any service can opt out locally with:

```bash
EUREKA_ENABLED=false
```
