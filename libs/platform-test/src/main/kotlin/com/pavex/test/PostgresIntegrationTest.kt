package com.pavex.test

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable

/**
 * Boots the whole service against a real PostgreSQL ([PavexPostgres]): Flyway runs every migration
 * and Hibernate checks each entity against the tables (`ddl-auto=validate`).
 *
 * Nothing else has to run: Eureka is off, RabbitMQ listeners do not start and publishers point at a
 * closed port (never the local broker of `compose.yml`), and the service keys are test keys
 * ([TestKeys]). Settings only one service needs stay in its `src/test/resources/application-test.yaml`.
 *
 * Without Docker the test class is skipped, not failed.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIfDockerAvailable
@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        // Known before the container starts, so RequiredSettingsCheck sees the password as set.
        "spring.datasource.username=${PavexPostgres.USERNAME}",
        "spring.datasource.password=${PavexPostgres.PASSWORD}",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=1",
        "spring.rabbitmq.password=test",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "management.health.rabbit.enabled=false",
        "pavex.storage.secret-key=test",
        "pavex.bootstrap-admin.enabled=false",
    ],
)
@ContextConfiguration(initializers = [PavexPostgres.Initializer::class])
annotation class PostgresIntegrationTest
