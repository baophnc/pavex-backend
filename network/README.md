# Pavex Network Service

Patch 01 establishes only the network foundation. Transport lanes, schedules,
route templates and capacity reservations are intentionally deferred to later
patches.

## Domain decisions

- `parentHubId` models organizational ownership only. It does not constrain
  transport routing.
- Regions are database records, not a hard-coded province-to-region enum map.
- A service area points to one primary operational hub and may own many
  province/ward coverages.
- A local hub may serve many wards; coverage is not required to match the hub's
  own ward.
- Ward coverage overrides province coverage during resolution.
- Only active hubs explicitly marked `publicVisible=true` appear in the public
  hub directory.

## Local development

- HTTP: `8085`
- PostgreSQL: `localhost:5436/pavex_network`
- Eureka service id: `network-service`
