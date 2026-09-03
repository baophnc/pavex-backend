# Authorization scope foundation

Pavex separates **capability** from **data scope**:

- permissions answer **what may the actor do?**
- access scopes answer **which rows/resources may the actor touch?**

The shared scope vocabulary is defined in `platform-common`:

- `GLOBAL`
- `ASSIGNED_HUBS`
- `OWN`
- `OWN_MERCHANT`
- `ASSIGNED_TO_ME`

`HubAccessScope` is the concrete shared primitive currently consumed by Operations and Shipment. Global scope never carries hub ids; assigned-hub scope always evaluates access through the resolved active hub-membership set.

## User principal

User-facing services should use `Authentication.requirePavexActor()` instead of parsing `authentication.name` ad hoc. It validates that the JWT subject is a UUID and exposes the normalized authority set.

## Operations hub resolver

`WorkforceHubScopeResolver` is the single source for resolving active Operations hub membership. Suspended/offboarded workforce members receive an empty assigned-hub scope even if stale active membership rows still exist.

Shipment's hub-scoped administrative features continue to ask Operations for the hub snapshot. This preserves Operations as the owner of workforce/hub membership data.

## Patch boundary

This patch creates and adopts the scope foundation. It deliberately does **not** broaden Shipment exception mutations or other sensitive endpoints. Those query/mutation predicates are applied in Patch 4.
