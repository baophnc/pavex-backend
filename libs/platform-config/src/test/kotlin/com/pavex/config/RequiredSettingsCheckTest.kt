package com.pavex.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.OriginTrackedMapPropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

class RequiredSettingsCheckTest {

    private fun environment(vararg config: Pair<String, Any>) = StandardEnvironment().apply {
        propertySources.addLast(OriginTrackedMapPropertySource("Config resource 'application.yaml'", mapOf(*config)))
    }

    @Test
    fun `reports each unset variable once`() {
        val environment = environment(
            "spring.datasource.password" to "\${BILLING_DB_PASSWORD}",
            "spring.rabbitmq.password" to "\${RABBITMQ_PASSWORD}",
            "other.rabbitmq.password" to "\${RABBITMQ_PASSWORD}",
            "spring.rabbitmq.username" to "\${RABBITMQ_USERNAME:pavex}",
            "server.port" to 8088,
        )

        assertEquals(listOf("BILLING_DB_PASSWORD", "RABBITMQ_PASSWORD"), RequiredSettingsCheck.missingSettings(environment))
        val error = assertThrows<IllegalStateException> {
            RequiredSettingsCheck().postProcessEnvironment(environment, SpringApplication())
        }
        assertTrue(error.message!!.contains("BILLING_DB_PASSWORD, RABBITMQ_PASSWORD"))
    }

    @Test
    fun `a value from the environment or a later file satisfies the setting`() {
        val environment = environment("spring.datasource.password" to "\${BILLING_DB_PASSWORD}")
        environment.propertySources.addFirst(MapPropertySource("env", mapOf("BILLING_DB_PASSWORD" to "secret")))

        assertEquals(emptyList<String>(), RequiredSettingsCheck.missingSettings(environment))
        RequiredSettingsCheck().postProcessEnvironment(environment, SpringApplication())
    }

    @Test
    fun `a profile file overriding the setting satisfies it`() {
        val environment = environment("spring.datasource.password" to "\${BILLING_DB_PASSWORD}")
        environment.propertySources.addFirst(
            OriginTrackedMapPropertySource("Config resource 'application-test.yaml'", mapOf("spring.datasource.password" to "")),
        )

        assertEquals(emptyList<String>(), RequiredSettingsCheck.missingSettings(environment))
    }
}
