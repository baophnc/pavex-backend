# Pavex Platform Security

Resource-server and service-token security that every business service configures the same way.
A separate module rather than part of `platform-common`, because it is Spring (Security, Boot
auto-configuration) and `platform-common` is deliberately framework-free.

## What a service gets

- `userJwtDecoder` (auto-configured from `security.jwt.public-key` and `security.jwt.issuer`): Auth's
  access tokens, checked for signature, expiry, issuer and `typ=user`.
- `serviceJwtEncoder` (auto-configured when `security.service-jwt.private-key` is set): signs this
  service's own service tokens. `ServiceTokenIssuer` builds them: `typ=service`, `sub` = the service,
  one `aud`, a fresh `jti`, the scopes as one space-separated `scope` claim, TTL `security.service-jwt.ttl`.
- `TrustedServiceTokens`: a decoder per trusted caller, from `security.trusted-services.<name>.public-key`
  and `.issuer` (or any other prefix), checking `sub` = caller, `typ=service`, this service in `aud`
  and, if given, one required scope. `IssuerRoutingJwtDecoder` picks one of several by issuer.
- `http.userTokenChain(decoder) { rules }`: stateless chain, OpenAPI/health/Prometheus paths open,
  authorities from `PavexJwtAuthenticationConverter` (`roles` → `ROLE_<role>`, `permissions` as is).
- `http.serviceTokenChain(decoder, "/internal/v1/x/**") { rules }`: stateless chain for one internal
  path set; authorities are the token's scopes as `SCOPE_<scope>`; default rule: any valid token.

## What stays in the service

Its `SecurityConfig` (request matchers per chain, chain order), which callers it trusts and the scope
each must carry (`InternalServiceJwtConfig`), and its `ServiceJwtIssuer` (the audiences and scopes of
the tokens it sends). Auth also keeps the encoder that signs user tokens.
