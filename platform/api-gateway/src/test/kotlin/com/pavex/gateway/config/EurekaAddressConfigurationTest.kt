package com.pavex.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean
import org.springframework.core.env.MapPropertySource
import org.springframework.core.io.FileSystemResource
import java.nio.file.Files
import java.nio.file.Path

class EurekaAddressConfigurationTest {
    private val modules = listOf(
        "auth", "identity", "partners", "network", "pricing", "shipment", "operations", "billing", "fleet",
        "notifications", "platform/api-gateway",
    )

    @Test
    fun `local env file pins every Eureka client to loopback, never a macOS local hostname`() {
        for (module in modules) {
            val config = bind(module, mapOf("EUREKA_INSTANCE_IP_ADDRESS" to "127.0.0.1", "EUREKA_INSTANCE_HOSTNAME" to "127.0.0.1"))

            assertTrue(config.isPreferIpAddress, module)
            assertEquals("127.0.0.1", config.ipAddress, module)
            assertEquals("127.0.0.1", config.getHostName(false), module)
            assertEquals("127.0.0.1", config.getHostName(true), "$module must not refresh back to the machine hostname")
        }
    }

    @Test
    fun `without the local values every Eureka client advertises the detected address, not loopback`() {
        for (module in modules) {
            val config = bind(module, mapOf("spring.cloud.client.ip-address" to "10.0.0.7", "spring.cloud.client.hostname" to "pavex-$module"))

            assertTrue(config.isPreferIpAddress, module)
            assertEquals("10.0.0.7", config.ipAddress, module)
            assertEquals("10.0.0.7", config.getHostName(true), module)
        }
    }

    private fun bind(module: String, settings: Map<String, Any>): EurekaInstanceConfigBean {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("compose.yml")) }
        val inet = mock(InetUtils::class.java)
        val host = InetUtils.HostInfo().apply {
            hostname = "Bao-Phuc-2.local"
            ipAddress = "10.0.0.99"
        }
        `when`(inet.findFirstNonLoopbackHostInfo()).thenReturn(host)
        val config = EurekaInstanceConfigBean(inet)
        val resource = FileSystemResource(root.resolve("$module/src/main/resources/application.yaml"))
        val sources = listOf(MapPropertySource("settings", settings)) + YamlPropertySourceLoader().load(module, resource)
        Binder(ConfigurationPropertySources.from(sources), PropertySourcesPlaceholdersResolver(sources))
            .bind("eureka.instance", Bindable.ofInstance(config))
        return config
    }
}
