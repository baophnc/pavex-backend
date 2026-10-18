package com.pavex.operations

import com.pavex.test.PostgresIntegrationTest
import com.pavex.test.SchemaAssertions
import jakarta.persistence.EntityManagerFactory
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/** The operations migrations run on a real PostgreSQL and the entities match the tables they create. */
@PostgresIntegrationTest
class OperationsDatabaseTest {
    @Autowired
    private lateinit var flyway: Flyway

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Test
    fun `migrations apply and every entity matches its table`() {
        SchemaAssertions.assertMigratedAndValidated(flyway, entityManagerFactory)
    }
}
