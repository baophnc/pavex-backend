# JWT keys and local secrets

## Trust boundary

Pavex uses separate RSA identities for separate issuers:

- `auth` signs user access tokens;
- `identity`, `partners`, `pricing`, `shipment`, and `operations` each sign their own service-to-service JWTs;
- receiving services trust only the public key for the specific issuer they accept.

Do not reuse the Auth private key as a service key. Compromise of one service key must not allow an attacker to mint user tokens or impersonate another service.

## Local development

From the repository root run:

```bash
bash scripts/security-bootstrap-local.sh
```

The script preserves the repository's existing `.gitignore`, appends only missing security/build ignore rules, creates untracked keypairs under `.local/keys/<issuer>/`, and removes the legacy checked-in key files. The default non-production Spring configuration reads those local keypaths automatically.

The local Auth bootstrap password is intentionally not stored in source. Set it in the run environment, for example in IntelliJ's Run/Debug configuration:

```text
BOOTSTRAP_ADMIN_PASSWORD=<your local development password>
```

Do not put real credentials in `application-local.yaml` or commit a `.env` containing secrets.

## Production

Run with the `prod` Spring profile and provide all required issuer/key/database secrets through the deployment secret store. `application-prod.yaml` deliberately has no key or database-password fallback. Missing security material should make startup fail instead of silently using a development credential.

At minimum, each deployment must provide the relevant values such as:

- `JWT_ISSUER`, `JWT_PUBLIC_KEY` and, for Auth, `JWT_PRIVATE_KEY`;
- `<SERVICE>_SERVICE_JWT_ISSUER`;
- `<SERVICE>_SERVICE_JWT_PRIVATE_KEY` and `<SERVICE>_SERVICE_JWT_PUBLIC_KEY` for services that issue service tokens;
- public keys and issuers for explicitly trusted upstream services;
- production database credentials and messaging credentials where used.

## If a private key was committed before this patch

Treat it as compromised even after deleting it from the working tree.

1. Generate a new production keypair and deploy the new public/private material through the secret store.
2. Revoke/stop trusting the old public key everywhere.
3. Purge the legacy private-key paths from Git history using your repository-history rewrite procedure (for example `git filter-repo`) and force-push only after coordinating with all collaborators.
4. Re-clone or carefully reset developer clones after the history rewrite.

The distributed Patch 01 intentionally does **not** contain PEM deletion hunks because a normal text deletion hunk would copy the old private-key contents into the patch itself. `scripts/security-bootstrap-local.sh` performs those removals locally instead.
