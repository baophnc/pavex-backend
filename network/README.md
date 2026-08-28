# Pavex Network Service

Patch 01 establishes regions, hubs, service areas, coverage resolution and the
public hub directory. Patch 02 adds the transport graph and time-aware route
planning.

## Domain decisions

- `parentHubId` models organizational ownership only. It never constrains a
  transport path.
- Regions are database records, not a hard-coded province-to-region enum map.
- A service area points to one primary operational hub and may own many
  province/ward coverages.
- Ward coverage overrides province coverage during resolution.
- Only active hubs explicitly marked `publicVisible=true` appear publicly.
- A `HubLane` is a directed transport edge between any two different hubs.
  Hub hierarchy is not consulted when creating or planning lanes.
- A new lane starts `INACTIVE`. At least one active current/future schedule and
  two active endpoint hubs are required before it can be activated.
- A lane can have many weekly schedules, including several departure times on
  the same day.
- Route planning minimizes actual arrival time (waiting + transfer buffer +
  transit), not merely the sum of transit minutes.
- Vietnam time (`Asia/Ho_Chi_Minh`) is used to resolve recurring departures.
- Capacity is intentionally deferred to Patch 04. Patch 02 only filters on a
  lane's per-shipment maximum weight.

## Transport API

Admin lane endpoints:

- `GET /api/v1/admin/network/lanes`
- `GET /api/v1/admin/network/lanes/{laneId}`
- `POST /api/v1/admin/network/lanes`
- `PUT /api/v1/admin/network/lanes/{laneId}`
- `PATCH /api/v1/admin/network/lanes/{laneId}/status`

Schedules:

- `GET /api/v1/admin/network/lanes/{laneId}/schedules`
- `GET /api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}`
- `POST /api/v1/admin/network/lanes/{laneId}/schedules`
- `PUT /api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}`
- `PATCH /api/v1/admin/network/lanes/{laneId}/schedules/{scheduleId}/status`

Routing:

- `GET /api/v1/admin/network/routing/resolve`
- `GET /api/v1/admin/network/routing/plan`

## Local development

- HTTP: `8085`
- PostgreSQL: `localhost:5436/pavex_network`
- Eureka service id: `network-service`
- Default inter-hub transfer buffer: `30` minutes
- Default recurring-schedule search horizon: `30` days

## Route templates (patch 03)

Route templates replace the old branch-route concept with an explicit operator preference layered on top of the transport graph.

- Templates are created inactive and contain an ordered, contiguous, cycle-free list of hub lanes.
- Activating a template requires every lane, hub, and at least one current/future schedule per lane to be operational.
- Active templates are evaluated by ascending priority; the first template that can produce a scheduled journey is used.
- Routing defaults to `PREFER_TEMPLATE` and falls back to the earliest-arrival graph planner when no template is currently usable.
- Admins can use `GRAPH_ONLY` or `TEMPLATE_ONLY` on the route-plan endpoint for diagnostics.
- Updating a template increments its business `revision`; route-plan responses record the template id, code and revision used.
- `GET /api/v1/admin/network/route-templates/journey` previews the highest-priority currently usable template directly between two hubs.
- Inactive templates can be deleted; active templates must be deactivated first so audit history and operational intent stay explicit.
