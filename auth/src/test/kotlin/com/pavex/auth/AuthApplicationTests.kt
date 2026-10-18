package com.pavex.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApplicationTests {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Test
    fun contextLoads() {
    }

    @Test
    fun `login with missing email returns 400 not misleading 401`() {
        val response = postLogin("""{"login":"admin@pavex.com","password":"any-password"}""")
        assertEquals(400, response.statusCode())
        assertTrue(response.body().contains("invalid_request_body"))
    }

    @Test
    fun `malformed login JSON returns 400`() {
        assertEquals(400, postLogin("{broken").statusCode())
    }

    @Test
    fun `protected API still rejects anonymous requests`() {
        val response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/auth/protected")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(401, response.statusCode())
    }

    private fun postLogin(body: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString(),
    )
}
