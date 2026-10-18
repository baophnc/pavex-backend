package com.pavex.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtValidationException
import java.time.Duration
import java.time.Instant

class PavexJwtDecodersTest {
    private val auth = TestKeyPair()
    private val shipment = TestKeyPair()
    private val users = PavexJwtDecoders.userTokens(auth.publicKey, AUTH_ISSUER)
    private val shipmentCalls = PavexJwtDecoders.serviceTokens(
        shipment.publicKey,
        SHIPMENT_ISSUER,
        caller = "shipment-service",
        audience = "network-service",
        requiredScope = "network.shipments.reserve",
    )

    @Test
    fun `user decoder accepts Auth's user tokens only`() {
        val token = auth.sign(mapOf("iss" to AUTH_ISSUER, "sub" to "u1", "typ" to "user"))
        assertEquals("u1", users.decode(token).subject)

        assertRejected("JWT token type is invalid") {
            users.decode(auth.sign(mapOf("iss" to AUTH_ISSUER, "sub" to "u1", "typ" to "service")))
        }
        assertThrows(JwtValidationException::class.java) {
            users.decode(auth.sign(mapOf("iss" to "https://elsewhere", "sub" to "u1", "typ" to "user")))
        }
        assertThrows(BadJwtException::class.java) {
            users.decode(shipment.sign(mapOf("iss" to AUTH_ISSUER, "sub" to "u1", "typ" to "user")))
        }
    }

    @Test
    fun `service decoder checks caller, token type, audience and scope`() {
        val valid = serviceClaims()
        assertEquals("shipment-service", shipmentCalls.decode(shipment.sign(valid)).subject)
        assertEquals("shipment-service", shipmentCalls.decode(shipment.sign(valid + ("scope" to "a network.shipments.reserve b"))).subject)

        assertRejected("Invalid sub claim") { shipmentCalls.decode(shipment.sign(valid + ("sub" to "billing-service"))) }
        assertRejected("Invalid typ claim") { shipmentCalls.decode(shipment.sign(valid + ("typ" to "user"))) }
        assertRejected("Invalid audience") { shipmentCalls.decode(shipment.sign(valid + ("aud" to listOf("pricing-service")))) }
        assertRejected("Invalid scope") { shipmentCalls.decode(shipment.sign(valid + ("scope" to "network.shipments.read"))) }
        assertRejected("Invalid scope") { shipmentCalls.decode(shipment.sign(valid - "scope")) }
        assertThrows(BadJwtException::class.java) { shipmentCalls.decode(auth.sign(valid)) }
    }

    @Test
    fun `service decoder without a required scope accepts any scope`() {
        val anyScope = PavexJwtDecoders.serviceTokens(shipment.publicKey, SHIPMENT_ISSUER, "shipment-service", "partners-service")
        anyScope.decode(shipment.sign(serviceClaims(audience = "partners-service") - "scope"))
    }

    @Test
    fun `issued service tokens pass the receiving service's decoder`() {
        val issuer = ServiceTokenIssuer(shipment.encoder, SHIPMENT_ISSUER, "shipment-service", Duration.ofMinutes(1))
        val now = Instant.now()
        val jwt = shipmentCalls.decode(issuer.issue("network-service", listOf("network.shipments.reserve", "other"), now))

        assertEquals("service", jwt.getClaimAsString("typ"))
        assertEquals("network.shipments.reserve other", jwt.getClaimAsString("scope"))
        assertEquals(listOf("network-service"), jwt.audience)
        assertEquals(now.plusSeconds(60).epochSecond, jwt.expiresAt!!.epochSecond)
        assertTrue(!jwt.id.isNullOrBlank())
        val second = shipmentCalls.decode(issuer.issue("network-service", "network.shipments.reserve", now))
        assertTrue(second.id != jwt.id)
    }

    @Test
    fun `issuer routing picks the decoder by issuer and rejects unknown issuers`() {
        val billing = TestKeyPair()
        val router = IssuerRoutingJwtDecoder(
            mapOf(
                SHIPMENT_ISSUER to PavexJwtDecoders.serviceTokens(shipment.publicKey, SHIPMENT_ISSUER, "shipment-service", "partners-service"),
                BILLING_ISSUER to PavexJwtDecoders.serviceTokens(billing.publicKey, BILLING_ISSUER, "billing-service", "partners-service"),
            ),
        )
        assertEquals("shipment-service", router.decode(shipment.sign(serviceClaims(audience = "partners-service"))).subject)
        assertEquals(
            "billing-service",
            router.decode(billing.sign(serviceClaims(audience = "partners-service") + ("iss" to BILLING_ISSUER) + ("sub" to "billing-service"))).subject,
        )

        val unknown = assertThrows(BadJwtException::class.java) {
            router.decode(shipment.sign(serviceClaims() + ("iss" to "https://stranger")))
        }
        assertEquals("Service token issuer is not trusted.", unknown.message)
        assertEquals("Malformed service token.", assertThrows(BadJwtException::class.java) { router.decode("not-a-jwt") }.message)
        // The routed decoder still checks the signature: shipment's issuer, billing's key.
        assertThrows(BadJwtException::class.java) { router.decode(billing.sign(serviceClaims(audience = "partners-service"))) }
    }

    private fun serviceClaims(audience: String = "network-service"): Map<String, Any> = mapOf(
        "iss" to SHIPMENT_ISSUER,
        "sub" to "shipment-service",
        "aud" to listOf(audience),
        "typ" to "service",
        "scope" to "network.shipments.reserve",
    )

    private fun assertRejected(description: String, decode: () -> Unit) {
        val exception = assertThrows(JwtValidationException::class.java) { decode() }
        assertTrue(exception.errors.any { it.description == description }, "${exception.errors} should contain $description")
    }

    companion object {
        private const val AUTH_ISSUER = "http://localhost:8081"
        private const val SHIPMENT_ISSUER = "http://localhost:8084"
        private const val BILLING_ISSUER = "http://localhost:8088"
    }
}
