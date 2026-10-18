package com.pavex.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// A route is needed for the gateway's CORS handling; a preflight never reaches its target.
private const val PROBE_ROUTE_ID = "spring.cloud.gateway.server.webflux.routes[0].id=cors-probe"
private const val PROBE_ROUTE_URI = "spring.cloud.gateway.server.webflux.routes[0].uri=http://127.0.0.1:9"
private const val PROBE_ROUTE_PATH = "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/v1/**"

private val client: HttpClient = HttpClient.newHttpClient()

private fun preflight(port: Int, origin: String): HttpResponse<String> =
    client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/auth/login"))
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "POST")
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )

/** GATEWAY_ALLOWED_ORIGIN takes a comma-separated list: the portals plus the field app's web build. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "GATEWAY_ALLOWED_ORIGIN=http://localhost:3000, http://localhost:3002,http://192.168.1.20:3002",
        PROBE_ROUTE_ID, PROBE_ROUTE_URI, PROBE_ROUTE_PATH,
    ],
)
@ActiveProfiles("test")
class CorsAllowedOriginsTest {
    @LocalServerPort
    var port = 0

    @Test
    fun `every listed origin passes the preflight`() {
        for (origin in listOf("http://localhost:3000", "http://localhost:3002", "http://192.168.1.20:3002")) {
            val response = preflight(port, origin)
            assertEquals(200, response.statusCode(), origin)
            assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        }
    }

    @Test
    fun `an origin outside the list is refused`() {
        val response = preflight(port, "http://evil.example")
        assertEquals(403, response.statusCode())
        assertNull(response.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [PROBE_ROUTE_ID, PROBE_ROUTE_URI, PROBE_ROUTE_PATH],
)
@ActiveProfiles("test")
class CorsDefaultOriginTest {
    @LocalServerPort
    var port = 0

    @Test
    fun `without the setting only the local client portal is allowed`() {
        assertEquals(200, preflight(port, "http://localhost:3000").statusCode())
        assertEquals(403, preflight(port, "http://localhost:3002").statusCode())
    }
}
