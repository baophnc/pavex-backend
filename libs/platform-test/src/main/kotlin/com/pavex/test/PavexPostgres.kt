package com.pavex.test

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * One PostgreSQL container per test JVM (Maven runs each service's tests in its own JVM), on a
 * random host port. Testcontainers removes it when the JVM exits.
 */
object PavexPostgres {
    /** Same image as the databases in compose.yml. */
    const val IMAGE = "postgres:18.6-alpine"
    const val USERNAME = "pavex"
    const val PASSWORD = "pavex"

    val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer(IMAGE)
            .withDatabaseName("pavex")
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .apply { start() }
    }

    /** Points the service at the container and at the test keys; wins over every configuration file. */
    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            context.environment.propertySources.addFirst(
                MapPropertySource(
                    "pavexPostgres",
                    mapOf(
                        "spring.datasource.url" to container.jdbcUrl,
                        "spring.datasource.username" to USERNAME,
                        "spring.datasource.password" to PASSWORD,
                        "pavex.keys-dir" to TestKeys.directory.toString(),
                    ),
                ),
            )
        }
    }
}
