# Local configuration and login

Each of the nine services has exactly one runtime configuration:
`src/main/resources/application.yaml`. YAML under `src/test/resources` is
test-only. There are no local/prod/gmail profile overlays and no imported
bootstrap properties. This setup is intentionally **local only**, not production-ready.

## Keys

Run once (also called by `scripts/dev-up.sh`):

```bash
bash scripts/security-bootstrap-local.sh
```

All services, whether launched from IntelliJ or the script, use
`~/.pavex/keys/<issuer>/{private,public}.pem`. Six issuers retain separate
RSA keypairs: auth, identity, partners, pricing, shipment, operations.
No private keys are committed, embedded in jars, or shared between issuers.
Existing private keys are preserved; public keys are derived from them.
Docker is needed for databases/messaging, **not** key generation.

Legacy `*/.keys`, Docker key volumes, `.local/dev.env`, and
`~/.pavex/bootstrap-admin.properties` are no longer consumed by the startup
scripts/configuration. They have not been deleted. Remove old explicit key,
password, profile and `spring.config.*` overrides from IntelliJ Run configurations.
Spring command-line/direct property overrides still take precedence over YAML.
Restart every service together when changing the key source: old JWTs must be
discarded and users must log in again.

Auth's local HMAC and outbox encryption keys are defined in its YAML.
Do not change the outbox key while encrypted messages are pending. Messages
created previously with a different `.local/dev.env` encryption key require
that original key to decrypt; switching to the YAML default does not migrate them.

## Bootstrap administrator

Auth and Identity bootstrap the same UUID and `admin@pavex.com`.
Auth sets the password to `1205@Phuc` (no backslash), verifies email and clears
local lockout state at startup. Identity activates the account and assigns
the platform admin role. Both services must finish startup before login.
No demo Identity/Auth/workforce/merchant accounts are created. Create test
accounts through the normal APIs and invitation flow. To disable local
bootstrap/reset behavior, edit the Auth and Identity service YAML.

Start infrastructure and services with:

```bash
bash scripts/dev-up.sh
```

IntelliJ: start infrastructure with `docker compose up -d --wait`, then Discovery,
Identity, Auth and the remaining services. Wait for Eureka registration.
No active Spring profile is required. The launcher skips already-healthy
processes; stop old processes first when applying new configuration/builds.

Direct backend login uses `POST /api/v1/auth/login` with JSON fields
`email` and `password`, not `login`. Send no stale Bearer token on login.
401 means invalid credentials/token; malformed login JSON should return 400;
Identity connectivity/key-validation failures should return 503, not be
presented as a wrong password.

Before production, reintroduce deployment configuration, secret management,
least-privilege private-key mounts, non-demo credentials and disabled bootstraps.
Do not expose this local stack or its known test credentials publicly.
