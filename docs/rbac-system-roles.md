# Pavex system RBAC policy

Identity owns the user-facing RBAC catalog. Auth only consumes the authorization
snapshot returned by Identity when it issues tokens.

## Scope model

A permission answers **what the actor may do**. The owning domain service answers
**which rows/resources the actor may do it to**.

| System role | Intended data scope |
| --- | --- |
| `ADMIN` | Global platform |
| `OPERATIONS_MANAGER` | Global operations |
| `HUB_MANAGER` | Assigned hubs |
| `WAREHOUSE_STAFF` | Assigned hubs + own assignments |
| `DRIVER` | Own assignments / deliveries |
| `MERCHANT` | Own merchant account / shipments |
| `CUSTOMER` | Own customer-facing data |

Do not create permissions such as `*.manage-hub-a` or one permission per hub.
Hub membership and assignment ownership belong to the Operations/Shipment data
scope layer, not to the permission name.

## Transitional shipment permissions

`shipments.read` is retained temporarily because the current merchant shipment
controller checks it before applying ownership filtering. `shipments.read-own`
is the canonical scoped capability and will replace the legacy check when the
access-scope patch lands.

`HUB_MANAGER` intentionally does **not** receive `shipments.cases.manage` or
`shipments.control-tower.read` in this patch. In the current shipment-service
those permissions mean global access. Hub-manager access continues through
`ROLE_HUB_MANAGER + operations.hub.manage` until Shipment is converted to the
unified access-scope policy.

Likewise, `HUB_MANAGER` does not receive `operations.workforce.manage` or
`operations.assignments.manage`; those currently mean global Operations access.
`operations.hub.manage` is the existing hub-scoped management capability.

## Ownership rules

The seven system roles are protected from permission-matrix edits by the
Identity service. Custom roles may use the backend-owned permission catalog, but
the owning service must still enforce resource scope server-side. Frontend route
or navigation checks are presentation only and must never be the authorization
boundary.
