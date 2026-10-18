# Pavex Platform Config

`platform-config` holds Spring Boot startup checks on configuration that every business service shares.

## RequiredSettingsCheck

A setting written without a default in `application.yaml`, such as `password: ${BILLING_DB_PASSWORD}`, is
required. Spring Boot alone would start the service with the literal text `${BILLING_DB_PASSWORD}`; this
check stops startup instead and names every unset variable. Settings with a default (`${X:value}`) are optional.

Locally `./scripts/dev-up.sh` writes the local values into `.env`; for a real deployment see
"Triển khai thật" in `docs/PAVEX-TAI-LIEU-TONG-HOP.md`.
