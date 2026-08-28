package com.pavex.auth.service

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.IdentityServiceUnavailableException
import com.pavex.contracts.identity.IdentityAuthContext
import com.pavex.contracts.identity.ProvisionIdentityRequest
import com.pavex.contracts.identity.UpdateIdentityEmailVerificationRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Component
class IdentityClient(
    @LoadBalanced builder: RestClient.Builder,
    @Value("\${auth.identity.service-url:http://identity-service}")
    serviceUrl: String,
    @Value("\${auth.identity.internal-token:}")
    private val internalToken: String,
) {

    private val client = builder
        .baseUrl(serviceUrl)
        .build()

    fun findAuthContext(
        userId: UUID,
    ): IdentityAuthContext? =
        try {
            fetchAuthContext(userId)
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw IdentityServiceUnavailableException()
            }
        } catch (exception: RestClientException) {
            throw IdentityServiceUnavailableException()
        }

    fun getAuthContext(
        userId: UUID,
    ): IdentityAuthContext =
        try {
            fetchAuthContext(userId)
        } catch (exception: IdentityServiceUnavailableException) {
            throw exception
        } catch (exception: RestClientException) {
            throw IdentityServiceUnavailableException()
        }

    fun provision(
        account: UserAccount,
        roles: Set<String>,
        displayName: String? = null,
        avatarUrl: String? = null,
    ) {
        val request = ProvisionIdentityRequest(
            userId = account.id,
            email = account.email,
            emailVerified = account.emailVerified,
            status = account.status.name,
            roles = roles,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )

        try {
            client
                .post()
                .uri("/internal/v1/identities")
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientException) {
            throw IdentityServiceUnavailableException()
        }
    }

    fun updateEmailVerification(
        userId: UUID,
        emailVerified: Boolean,
    ): Boolean =
        try {
            client
                .patch()
                .uri("/internal/v1/identities/{userId}/email-verification", userId)
                .header(INTERNAL_TOKEN_HEADER, internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(UpdateIdentityEmailVerificationRequest(emailVerified))
                .retrieve()
                .toBodilessEntity()
            true
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode == HttpStatus.NOT_FOUND) {
                false
            } else {
                throw IdentityServiceUnavailableException()
            }
        } catch (exception: RestClientException) {
            throw IdentityServiceUnavailableException()
        }

    private fun fetchAuthContext(
        userId: UUID,
    ): IdentityAuthContext =
        client
            .get()
            .uri("/internal/v1/identities/{userId}/auth-context", userId)
            .header(INTERNAL_TOKEN_HEADER, internalToken)
            .retrieve()
            .body(IdentityAuthContext::class.java)
            ?: throw IdentityServiceUnavailableException()

    companion object {
        private const val INTERNAL_TOKEN_HEADER =
            "X-Pavex-Internal-Token"
    }
}
