# Pavex Discovery Server

Local Eureka service registry for PAVEX microservices.

## Run

From repository root:

```bash
./mvnw -pl platform/discovery-server spring-boot:run
```

Dashboard: `http://localhost:8761`

Then start `identity-service`. It should appear in Eureka as `IDENTITY-SERVICE` with status `UP`.

Identity can opt out locally with:

```bash
EUREKA_ENABLED=false
```
