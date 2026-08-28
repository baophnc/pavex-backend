package com.pavex.identity.service

import com.pavex.contracts.identity.CredentialInvitationRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

@Component
class AuthCredentialClient(
    @LoadBalanced builder: RestClient.Builder,
    @Value("\${identity.internal-token:}") private val internalToken: String,
) {
    private val client = builder.baseUrl("http://auth-service").build()

    fun provisionInvitation(userId: UUID, email: String) =
        post("/internal/v1/credentials/invitations/provision", CredentialInvitationRequest(userId, email))

    fun sendInvitation(userId: UUID, email: String) =
        post("/internal/v1/credentials/invitations/send", CredentialInvitationRequest(userId, email))

    fun revokeSessions(userId: UUID) = post("/internal/v1/credentials/$userId/revoke-sessions", null)

    private fun post(path: String, body: Any?) {
        val request = client.post().uri(path).header(INTERNAL_TOKEN_HEADER, internalToken)
        if (body == null) request.retrieve().toBodilessEntity()
        else request.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity()
    }

    companion object { private const val INTERNAL_TOKEN_HEADER = "X-Pavex-Internal-Token" }
}
