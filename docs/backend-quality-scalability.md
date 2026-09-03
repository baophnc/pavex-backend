# Backend quality and scalability hardening

Patch 6 moves the high-volume Operations admin reads away from full-table in-memory filtering.

## Operations query rules

- Workforce, work-shift, and operational-assignment scope predicates are evaluated by PostgreSQL/JPA.
- Public one-based page numbers are converted to Spring Data zero-based pages with `OneBasedPagination`.
- A scoped actor with no active hub memberships returns an empty result without issuing an unrestricted query.
- Workforce enrichment (memberships, Identity snapshots, Network hub snapshots) runs only for rows in the returned page.
- Workforce text search still consults Identity because email/name/profile data is owned by Identity. The candidate phase now projects user IDs only instead of loading all workforce entities and memberships.

## Time

Operations services use an injected UTC `Clock`. One transaction should capture one timestamp and pass it into domain methods rather than repeatedly calling the system clock.

## Validation

Mutation DTOs in Operations carry Bean Validation constraints and controllers invoke them with `@Valid`. Domain services still enforce business invariants; validation only rejects malformed transport input earlier.

## Principal handling

Operations controllers resolve the authenticated UUID through `Authentication.requirePavexActor()` instead of parsing `Authentication.name` independently.

## Edge/proxy note

The backend services authenticate with bearer tokens, so browser CSRF protection belongs to the cookie-owning Management BFF. API Gateway client-IP rate limiting ignores forwarded headers by default. Set `GATEWAY_TRUSTED_PROXIES` to a comma-separated list of exact IPs or CIDRs for the deployment ingress/load balancers. Only a request whose direct peer is trusted may use `X-Forwarded-For`, and the resolver walks the chain from the trusted edge back to the first untrusted client address.
