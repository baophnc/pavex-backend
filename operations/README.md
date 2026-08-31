# Pavex Operations Service

`operations-service` owns the operational workforce, work shifts, assignment planning/execution and dispatch reliability boundary.
It has its own PostgreSQL database and must not read Identity, Network or Shipment tables directly.

## Patch 01 scope

This patch intentionally establishes the runtime boundary only:

- Kotlin + Spring Boot service on port `8087`
- dedicated PostgreSQL database `pavex_operations` on local port `5439`
- Flyway-owned `operations` schema
- Auth-issued user JWT validation with `roles` and `permissions`
- Eureka registration
- explicit API Gateway routes
- centralized Swagger/OpenAPI aggregation
- production profile that requires an external JWT public key

Patch 02 adds workforce management. Patch 03 adds work shifts and roster attendance. Assignment planning, Shipment integration and durable dispatch remain intentionally deferred to later patches.

## Local endpoints

- Service: `http://localhost:8087`
- Health: `http://localhost:8087/actuator/health`
- OpenAPI through Gateway: `http://localhost:8080/openapi/operations`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- PostgreSQL: `localhost:5439`, database `pavex_operations`

Start the database from the repository root:

```bash
docker compose up -d operations-db
```

Run the service from IntelliJ or Maven:

```bash
./mvnw -pl operations -am spring-boot:run
```

The API Gateway exposes only `/api/v1/operations/**` and `/api/v1/admin/operations/**` to this service. Method-level business authorization is added with the business endpoints in later patches.

## Patch 02 - Workforce

Patch 02 adds the operational workforce aggregate while preserving database-per-service boundaries:

- workforce member lifecycle (`ACTIVE`, `SUSPENDED`, `OFFBOARDED`)
- workforce kinds (`COURIER`, `WAREHOUSE_OPERATOR`, `DISPATCHER`, `LINE_HAUL_DRIVER`)
- active/history-preserving hub memberships with one primary hub
- availability/capacity foundation used by later shift/assignment patches
- immutable workforce audit records
- admin workforce search/detail/create/status/capacity/hub/audit APIs
- hub-scoped access for `operations.hub.manage`
- service-JWT Identity snapshots (`identity.workforce.read`)
- service-JWT Network hub snapshots (`network.operations.read`)

Operations never joins or reads Identity/Network databases. Identity owns user/role truth and Network owns hub truth. Patch 03 connects work shifts to availability; Patch 04 adds live assignment workload, so Patch 02 intentionally reports `activeAssignments = 0`.


## Patch 03 — Work shifts

Patch 03 ports the shift/attendance boundary from the original Operations module:

- admin search/detail/create/update/cancel shift APIs
- roster assignment/removal and no-show marking
- worker `shifts/mine`, check-in and check-out APIs
- 30-minute early check-in window
- overlap prevention for active roster assignments
- active Identity role + active hub membership revalidation before assignment/check-in
- automatic workforce availability transitions (`AVAILABLE` on check-in, `OFF_DUTY` on check-out/cancel/absence)
- optimistic-lock and database constraints for shift/roster correctness

Operational assignments remain Patch 04 scope.

## Patch 04 — Operational assignment core

Patch 04 adds the dispatchable work aggregate and worker execution queue:

- idempotent shipment assignment planning at the application layer;
- pickup, origin handling, line-haul, destination handling and last-mile work types;
- fair candidate selection by active load / configured capacity;
- candidate eligibility requires ACTIVE workforce, active hub membership, AVAILABLE state,
  an active checked-in shift at the assignment hub, and compatible live Identity roles;
- admin assignment search/manual assign/cancel with global or hub-scoped access;
- worker queue with accept/reject/start/complete and self availability;
- suspending/offboarding a member requeues assigned/accepted work and blocks when work is in progress;
- removing a hub membership follows the same handover invariant;
- workforce responses now report actual active assignment load and remaining capacity.

Shipment does not call the planner over HTTP yet. Patch 05 introduces the authenticated Shipment -> Operations
contract and removes Shipment-side fallback authorization/planning behavior.

## Patch 05 — Shipment integration

Patch 05 turns the Patch 04 planner into a real microservice boundary:

- Shipment authenticates with `sub=shipment-service`, `aud=operations-service`, `typ=service`, scope `operations.shipments.execute`;
- Network reservation snapshots expose their reserved route legs so Shipment can create the exact Operations assignment plan without reading Network storage;
- shipment creation plans Operations work before persistence and compensates both the assignment plan and Network reservation when creation aborts;
- shipment cancellation cancels Operations work before releasing Network capacity;
- parcel scans ask Operations for live hub capability and assignment ownership/readiness;
- pickup, hub handling and arriving line-haul assignments are completed from confirmed Shipment movement;
- delivery attempts require an in-progress owned last-mile assignment and complete it when all parcels are delivered.

Operations remains the owner of workforce/assignment state. Shipment never reads the Operations database. Patch 06 adds durable assignment events/outbox/redispatch so cross-service convergence no longer depends only on synchronous retries.
