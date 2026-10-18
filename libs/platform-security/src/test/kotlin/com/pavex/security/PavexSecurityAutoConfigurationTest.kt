package com.pavex.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class PavexSecurityAutoConfigurationTest {
    private val auth = TestKeyPair()
    private val own = TestKeyPair()
    private val shipment = TestKeyPair()
    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PavexSecurityAutoConfiguration::class.java))

    @Test
    fun `service with its own key pair gets the user decoder, the service encoder and trusted-caller decoders`(@TempDir keys: Path) {
        val authPublic = auth.writePem(Files.createDirectory(keys.resolve("auth"))).first
        val (ownPublic, ownPrivate) = own.writePem(Files.createDirectory(keys.resolve("network")))
        val shipmentPublic = shipment.writePem(Files.createDirectory(keys.resolve("shipment"))).first

        runner.withPropertyValues(
            "security.jwt.issuer=http://localhost:8081",
            "security.jwt.public-key=file:$authPublic",
            "security.service-jwt.private-key=file:$ownPrivate",
            "security.service-jwt.public-key=file:$ownPublic",
            "security.trusted-services.shipment.issuer=http://localhost:8084",
            "security.trusted-services.shipment.public-key=file:$shipmentPublic",
        ).run { context ->
            assertThat(context).hasBean("userJwtDecoder").hasBean("serviceJwtEncoder")
            val users = context.getBean("userJwtDecoder", JwtDecoder::class.java)
            users.decode(auth.sign(mapOf("iss" to "http://localhost:8081", "sub" to "u1", "typ" to "user")))

            val issued = ServiceTokenIssuer(context.getBean("serviceJwtEncoder", JwtEncoder::class.java), "http://localhost:8085", "network-service", Duration.ofMinutes(1))
                .issue("pricing-service", "pricing.quote.read")
            assertEquals("network-service", PavexJwtDecoders.serviceTokens(own.publicKey, "http://localhost:8085", "network-service", "pricing-service").decode(issued).subject)

            val trusted = context.getBean(TrustedServiceTokens::class.java)
                .trustedService("shipment", "shipment-service", "network-service", "network.shipments.reserve")
            val token = shipment.sign(
                mapOf(
                    "iss" to "http://localhost:8084",
                    "sub" to "shipment-service",
                    "aud" to listOf("network-service"),
                    "typ" to "service",
                    "scope" to "network.shipments.reserve",
                ),
            )
            assertEquals("shipment-service", trusted.decode(token).subject)
        }
    }

    @Test
    fun `service without its own key pair gets no encoder`(@TempDir keys: Path) {
        val authPublic = auth.writePem(keys).first
        runner.withPropertyValues(
            "security.jwt.issuer=http://localhost:8081",
            "security.jwt.public-key=file:$authPublic",
        ).run { context ->
            assertThat(context).hasBean("userJwtDecoder").doesNotHaveBean("serviceJwtEncoder").hasSingleBean(TrustedServiceTokens::class.java)
        }
    }
}
