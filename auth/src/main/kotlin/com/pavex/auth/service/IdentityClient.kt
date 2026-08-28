package com.pavex.auth.service

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.IdentityServiceUnavailableException
import com.pavex.auth.security.service.ServiceJwtIssuer
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
    private val serviceJwtIssuer: ServiceJwtIssuer,
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
            status = DEFAULT_IDENTITY_STATUS,
            roles = roles,
            displayName = displayName,
            avatarUrl = avatarUrl,
        )

        try {
            client
                .post()
                .uri("/internal/v1/identities")
                .headers { headers ->
                    headers.setBearerAuth(serviceJwtIssuer.issueForIdentity())
                }
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
                .headers { headers ->
                    headers.setBearerAuth(serviceJwtIssuer.issueForIdentity())
                }
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
            .headers { headers -> headers.setBearerAuth(serviceJwtIssuer.issueForIdentity()) }
            .retrieve()
            .body(IdentityAuthContext::class.java)
            ?: throw IdentityServiceUnavailableException()

    companion object {
        private const val DEFAULT_IDENTITY_STATUS = "ACTIVE"
    }
}
