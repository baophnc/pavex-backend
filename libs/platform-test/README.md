# Pavex Platform Test

Test support shared by the business services; each one depends on it with `<scope>test</scope>`.

## @PostgresIntegrationTest

Put it on a test class to boot the whole service against a real PostgreSQL:

- `PavexPostgres` starts one `postgres:18.6-alpine` container per test JVM (same image as `compose.yml`) on a random port,
  so a local stack on ports 5433–5442 is never touched.
- Flyway runs every migration and Hibernate validates the entities (`ddl-auto=validate`).
- Eureka is off, RabbitMQ listeners do not start and publishers point at a closed port; every service gets the same
  test key pair (`TestKeys`). Settings only one service needs go in its `src/test/resources/application-test.yaml`.
- Without Docker the class is skipped, not failed (`@EnabledIfDockerAvailable`).

`SchemaAssertions.assertMigratedAndValidated` is what each `<Service>DatabaseTest` checks. Billing's
`SettlementFlowDatabaseTest` shows a test that goes further: shipment events in, settlement statement out.
