# Authorization scope enforcement (Patch 4)

Patch 4 separates **capability** from **data scope** for operations-facing shipment features.

## Global operators

`ADMIN` and `OPERATIONS_MANAGER` may receive global scope when the required capability is present.
A permission by itself no longer implies global shipment scope.

## Hub-scoped operators

`HUB_MANAGER` (and read-only workforce roles where applicable) resolve active hub memberships from
`operations-service`. Shipment reads and mutations then predicate on those hub ids.

The following features are enforced at service/repository boundaries:

- Shipment Cases: read and manage are separate capabilities; both are hub-filtered for hub managers.
- Control Tower: `shipments.control-tower.read` is global only for global system roles; hub managers receive hub scope.
- Exception Queue: list and summary queries include `hub_id` predicates in SQL.
- Exception workflow mutations: requests and weight adjustments are bound to `hub_id`; review/complete operations reject cross-hub ids.
- Merchant shipment reads: use `shipments.read-own`; ownership remains enforced by `created_by_user_id` predicates.

## Legacy exception rows

`V7__scope_shipment_exceptions_by_hub.sql` backfills a hub only when one can be derived conservatively.
Rows that remain `NULL` are intentionally invisible to hub-scoped users and remain available only to global operators.
This is fail-closed by design.
