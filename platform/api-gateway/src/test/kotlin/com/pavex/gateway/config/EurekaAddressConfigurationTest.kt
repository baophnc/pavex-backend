package com.pavex.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.cloud.commons.util.InetUtils
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean
import org.springframework.core.io.FileSystemResource
import java.nio.file.Files
import java.nio.file.Path

class EurekaAddressConfigurationTest {
    @Test
    fun `runtime YAML overrides a macOS local hostname for every Eureka client`() {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.isRegularFile(it.resolve("compose.yml")) }
        val modules = listOf("auth", "identity", "partners", "network", "pricing", "shipment", "operations", "platform/api-gateway")
        for (module in modules) {
            val inet = mock(InetUtils::class.java)
            val host = InetUtils.HostInfo().apply {
                hostname = "Bao-Phuc-2.local"
                ipAddress = "10.0.0.99"
            }
            `when`(inet.findFirstNonLoopbackHostInfo()).thenReturn(host)
            val config = EurekaInstanceConfigBean(inet)
            val resource = FileSystemResource(root.resolve("$module/src/main/resources/application.yaml"))
            val sources = YamlPropertySourceLoader().load(module, resource)
            Binder(ConfigurationPropertySources.from(sources))
                .bind("eureka.instance", Bindable.ofInstance(config))

            assertTrue(config.isPreferIpAddress, module)
            assertEquals("127.0.0.1", config.ipAddress, module)
            assertEquals("127.0.0.1", config.getHostName(false), module)
            assertEquals("127.0.0.1", config.getHostName(true), "$module must not refresh back to the machine hostname")
        }
    }
}
