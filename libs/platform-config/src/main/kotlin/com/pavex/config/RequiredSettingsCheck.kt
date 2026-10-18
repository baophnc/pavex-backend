package com.pavex.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.boot.env.OriginTrackedMapPropertySource
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Stops a service at startup when its configuration refers to a setting nobody provided, such as
 * `password: ${BILLING_DB_PASSWORD}`. Spring Boot would otherwise bind the literal text
 * `${BILLING_DB_PASSWORD}` and start with a broken password or key. A placeholder with a default
 * (`${X:value}`) is optional and never reported.
 *
 * Runs after the configuration files (application.yaml, profile files, the local .env) are loaded.
 */
class RequiredSettingsCheck : EnvironmentPostProcessor, Ordered {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val missing = missingSettings(environment)
        check(missing.isEmpty()) {
            buildString {
                append("Missing required settings: ")
                append(missing.joinToString(", "))
                append(". Set them as environment variables (see \"Triển khai thật\" in docs/PAVEX-TAI-LIEU-TONG-HOP.md);")
                append(" on a local machine ./scripts/dev-up.sh writes the local values into .env.")
            }
        }
    }

    companion object {
        private val PLACEHOLDER_NAME = Regex("""placeholder '([^']+)'""")

        /** Names of the unset settings, e.g. `BILLING_DB_PASSWORD`, in alphabetical order. */
        fun missingSettings(environment: ConfigurableEnvironment): List<String> =
            environment.propertySources
                .filterIsInstance<OriginTrackedMapPropertySource>()
                .flatMap { it.propertyNames.asIterable() }
                .distinct()
                .mapNotNull { name -> unresolvedPlaceholder(environment, name) }
                .distinct()
                .sorted()

        private fun unresolvedPlaceholder(environment: ConfigurableEnvironment, name: String): String? =
            try {
                environment.getProperty(name)
                null
            } catch (e: IllegalArgumentException) {
                PLACEHOLDER_NAME.find(e.message.orEmpty())?.groupValues?.get(1) ?: name
            }
    }
}
