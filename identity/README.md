# Pavex Identity Service

Identity owns the user profile and the cross-service authorization context. It
runs independently on port `8082` with its own `pavex_identity` database.

This branch is the first safe extraction checkpoint. Auth remains the temporary
owner of legacy user administration/RBAC tables so existing APIs keep working;
new profile APIs and the internal identity contract live here. A later data
migration can move role assignments after consumers have switched to
`/internal/v1/identities/{userId}/auth-context`.

Internal endpoints require `X-Pavex-Internal-Token`. Configure the same strong
secret in callers through `IDENTITY_INTERNAL_TOKEN`; a blank value denies every
internal request.
