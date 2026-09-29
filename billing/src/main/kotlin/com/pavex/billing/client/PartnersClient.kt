package com.pavex.billing.client

import com.pavex.billing.exception.BillingForbiddenException
import com.pavex.billing.exception.BillingUnavailableException
import com.pavex.billing.security.ServiceJwtIssuer
import com.pavex.contracts.partners.MerchantAccountSnapshot
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/** Resolves which merchant a signed-in user acts for. */
@Component
class PartnersClient(
    @Qualifier("billingLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${billing.integrations.partners.service-url:http://partners-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun merchantOf(userId: UUID): MerchantAccountSnapshot? = try {
        client.get()
            .uri("/internal/v1/merchants/by-user/{userId}", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForPartners()}")
            .retrieve()
            .body(MerchantAccountSnapshot::class.java)
    } catch (_: HttpClientErrorException.NotFound) {
        null
    } catch (_: HttpClientErrorException.Unauthorized) {
        throw BillingUnavailableException("partners_authentication_failed", "Billing could not authenticate with Partners.")
    } catch (_: HttpClientErrorException.Forbidden) {
        throw BillingUnavailableException("partners_authorization_failed", "Billing is not authorized to read merchants.")
    } catch (_: RestClientException) {
        throw BillingUnavailableException("partners_unavailable", "Partners is temporarily unavailable. Please try again.")
    }

    /** The user's merchant id; merchant-only billing endpoints require one. */
    fun requireMerchantId(userId: UUID): UUID =
        merchantOf(userId)?.merchantId
            ?: throw BillingForbiddenException("merchant_required", "A merchant profile is required for billing.")
}
