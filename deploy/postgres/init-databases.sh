#!/bin/sh
# One database and one login per service, password from /run/secrets/<SERVICE>_DB_PASSWORD (gen-secrets.sh).
# PostgreSQL runs this on the first start of an empty data volume. Safe to rerun for a service added later:
#   docker compose -f deploy/compose.yml exec postgres sh /docker-entrypoint-initdb.d/10-pavex-databases.sh
set -eu

for pair in AUTH:pavex_auth IDENTITY:pavex_identity PARTNERS:pavex_partners NETWORK:pavex_network \
  PRICING:pavex_pricing SHIPMENT:pavex_shipments OPERATIONS:pavex_operations BILLING:pavex_billing \
  FLEET:pavex_fleet NOTIFICATIONS:pavex_notifications; do
  service="${pair%%:*}"
  name="${pair##*:}"
  password="$(cat "/run/secrets/${service}_DB_PASSWORD")"
  psql -v ON_ERROR_STOP=1 -U postgres -d postgres -v name="$name" -v password="$password" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'name', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'name') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'name', :'name')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'name') \gexec
SQL
  echo "[ready] database and login $name"
done
