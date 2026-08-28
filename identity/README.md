# Pavex Identity Service

Identity owns user identity, profile state, account lifecycle, roles,
permissions, and the authorization context consumed by Auth. It runs on port
`8082` with its own `pavex_identity` database.

Public ownership:

- `/api/v1/users/**`
- `/api/v1/roles/**`
- `/api/v1/identities/**`
- `/api/v1/auth/me` and `/api/v1/auth/me/profile`

Auth owns credentials, login/password flows, sessions, external authentication,
and user JWT issuance. Auth obtains status/roles/permissions from Identity
through `/internal/v1/identities/{userId}/auth-context`.

Internal service calls use short-lived RSA service JWTs, not a shared static
header secret. Auth -> Identity tokens must have `typ=service`,
`sub=auth-service`, `aud=identity-service`, and scope `identity.internal`.
Identity -> Auth uses the corresponding `identity-service` / `auth-service`
claims with scope `auth.internal`.

Service discovery is provided by Eureka; Eureka locates services but does not
authenticate callers.
