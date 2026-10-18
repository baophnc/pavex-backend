package com.pavex.test

import jakarta.persistence.EntityManagerFactory
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** What a [PostgresIntegrationTest] checks once the context is up. */
object SchemaAssertions {
    /**
     * Every migration of the service ran on PostgreSQL, and Hibernate validated the entities
     * against the resulting tables (the context would not have started otherwise).
     */
    fun assertMigratedAndValidated(flyway: Flyway, entityManagerFactory: EntityManagerFactory) {
        val info = flyway.info()
        assertTrue(info.applied().isNotEmpty(), "No Flyway migration was applied.")
        assertEquals(emptyList<String>(), info.pending().map { it.script }, "Migrations left pending")
        assertEquals(emptyList<String>(), info.applied().filter { it.state.isFailed }.map { it.script }, "Failed migrations")
        assertEquals("validate", entityManagerFactory.properties["hibernate.hbm2ddl.auto"], "Hibernate must validate the schema")
        assertTrue(entityManagerFactory.metamodel.entities.isNotEmpty(), "No JPA entity was mapped.")
    }
}
