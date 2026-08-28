package com.pavex.partners.service

import com.pavex.contracts.identity.MerchantEligibilityResponse
import com.pavex.partners.exception.IdentityServiceUnavailableException
import com.pavex.partners.security.service.ServiceJwtIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.util.UUID

@Component
class IdentityClient(
    @LoadBalanced builder: RestClient.Builder,
    @Value("\${partners.identity.service-url:http://identity-service}")
    serviceUrl: String,
    private val serviceJwtIssuer: ServiceJwtIssuer,
) {

    private val client = builder
        .baseUrl(serviceUrl)
        .build()

    fun findMerchantEligibility(
        userId: UUID,
    ): MerchantEligibilityResponse? =
        try {
            client
                .get()
                .uri(
                    "/internal/v1/identities/{userId}/merchant-eligibility",
                    userId,
                )
                .headers { headers ->
                    headers.setBearerAuth(serviceJwtIssuer.issueForIdentity())
                }
                .retrieve()
                .body(MerchantEligibilityResponse::class.java)
                ?: throw IdentityServiceUnavailableException()
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw IdentityServiceUnavailableException()
            }
        } catch (exception: RestClientException) {
            throw IdentityServiceUnavailableException()
        }
}
