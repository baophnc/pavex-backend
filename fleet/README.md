# Pavex Fleet Service

Fleet tracks line-haul trucks between hubs (post office → provincial warehouse → regional centre and
back). It does not track last-mile couriers, and customers never see positions.

- PostgreSQL schema `fleet` in database `pavex_fleet` (`fleet-db`, port 5441). Service port 8089.
- Route through the gateway: `/api/v1/fleet/**`.

## Trips

A trip is one driver's run on one lane departure, built from data other services own:

| Source | What it gives |
| --- | --- |
| Operations | The driver's open `LINE_HAUL` assignments (one per shipment and route leg) |
| Network | For each route leg: the lane, its two hubs and the planned departure and arrival |

Assignments on the same lane and planned departure ride the same truck, so they form one trip.

- **Start:** the driver presses "Start trip", or the app calls `start-from-scan` after the first
  "load onto truck" scan. The driver must have accepted the location terms (`/me/consent`).
- **Positions:** the app sends batches of up to 200 points while the trip is `IN_PROGRESS`. Each
  point carries the phone's own sequence number, so a resent batch is ignored.
- **End:** the driver presses "Arrived", or `TripArrivalMonitor` closes the trip once the
  destination hub has received every parcel (receiving completes the line-haul assignments).

## Who sees what

| Permission | Role | Scope |
| --- | --- | --- |
| `fleet.trips.report-own` | LINE_HAUL_DRIVER | Own trips only |
| `fleet.trips.read-hub` | HUB_MANAGER | Trips leaving or arriving at their hubs (Operations memberships) |
| `fleet.trips.read-hub` + `scope.all-hubs` | OPERATIONS_MANAGER, ADMIN | Every trip |

Opening a trip's track is recorded in `trip_access_log`.

## Storage

Raw points go to `trip_locations`, partitioned by UTC day. `TripLocationPartitions` creates the
partitions from yesterday to two days ahead every hour and drops days older than
`fleet.tracking.retention-days` (30). The latest position and distance travelled are kept on the
trip row for the live map.
