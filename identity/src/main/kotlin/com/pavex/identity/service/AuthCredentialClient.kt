package com.pavex.identity.service

import com.pavex.contracts.identity.CredentialInvitationRequest
import com.pavex.identity.security.service.ServiceJwtIssuer
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@Component
class AuthCredentialClient(
    @LoadBalanced builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
) {
    private val client = builder
        .baseUrl("http://auth-service")
        .build()

    fun provisionInvitation(
        userId: UUID,
        email: String,
    ) = post(
        path = "/internal/v1/credentials/invitations/provision",
        body = CredentialInvitationRequest(userId, email),
    )

    fun sendInvitation(
        userId: UUID,
        email: String,
    ) = post(
        path = "/internal/v1/credentials/invitations/send",
        body = CredentialInvitationRequest(userId, email),
    )

    fun revokeSessions(
        userId: UUID,
    ) = post(
        path = "/internal/v1/credentials/$userId/revoke-sessions",
        body = null,
    )

    private fun post(
        path: String,
        body: Any?,
    ) {
        val request = client
            .post()
            .uri(path)
            .headers { headers ->
                headers.setBearerAuth(serviceJwtIssuer.issueForAuth())
            }

        if (body == null) {
            request
                .retrieve()
                .toBodilessEntity()
            return
        }

        request
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }
}
