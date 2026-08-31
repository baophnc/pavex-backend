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

## Patch 06 — dispatch reliability

Assignment state changes are now captured transactionally by PostgreSQL into two durable stores:

- `operations.operational_assignment_event_logs` is the immutable assignment history.
- `operations.outbox_messages` is the integration-event delivery queue.

The database trigger records every assignment insert/status/assignee transition, including requeues caused by workforce or shift changes, so application code cannot accidentally mutate an assignment without creating history and an outbox message in the same transaction.

The outbox dispatcher publishes `operations.assignment.*` events to the durable `pavex.events` topic exchange with RabbitMQ publisher confirms. Multiple Operations instances safely claim different rows with `FOR UPDATE SKIP LOCKED`. Failed messages use exponential retry and are dead-lettered in the Operations database after the configured maximum attempts; Patch 07 exposes the admin inspection/retry/discard APIs.

The redispatch worker periodically considers `PENDING` and `REJECTED` assignments. It uses PostgreSQL advisory transaction locks for the assignment and workforce pool, excludes members who already rejected that assignment, and reuses the same shift/availability/capacity/Identity eligibility rules as initial planning.


## Patch 07 — admin, hardening and test closure

Patch 07 closes the Operations backend with production-facing administration and observability:

- assignment event history: `GET /api/v1/admin/operations/assignments/{id}/events`;
- scoped dispatch-event search: `GET /api/v1/admin/operations/dispatch-events`;
- global outbox summary and active dead-letter inspection;
- dead-letter retry/discard actions guarded by `operations.assignments.manage`;
- pessimistic row locking for concurrent dead-letter mutations;
- Micrometer counters for outbox processing/failures/dead-letter/manual actions and redispatch outcomes;
- audit actor propagation for admin cancellations, workforce-triggered requeues and shipment-driven completion;
- indexes for event/dead-letter administration queries;
- `/actuator/metrics/**` is exposed only to callers with `operations.assignments.manage`;
- expanded domain, access-scope, event-query and outbox administration tests.

With Patch 07 applied, Operations owns workforce, shifts, assignments, Shipment execution integration and durable dispatch reliability. Further Operations changes should be normal feature work rather than migration-foundation patches.
