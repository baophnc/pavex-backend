# Pavex Platform Contracts

`platform-contracts` contains DTOs that deliberately cross a service boundary: internal HTTP/gRPC contracts and integration-event envelopes.

It is a plain Kotlin/JVM library, not a deployable service.

## Rules

- Contracts are transport shapes, not JPA entities.
- Never expose a repository or service implementation through this module.
- Prefer strings/IDs and narrow DTOs over importing another service's internal domain model.
- Do not move domain enums here just because two services happen to use similarly named states.
- Breaking wire changes should create a new contract/event version rather than silently changing an existing published contract.

## Network pricing resolution

`com.pavex.contracts.network.NetworkLocationResolution*` is the deliberately narrow internal wire
contract used by Pricing to ask Network which service area, region, primary hub and coverage tier own
a province/ward pair. It does not expose Network JPA entities, route topology or capacity data.
