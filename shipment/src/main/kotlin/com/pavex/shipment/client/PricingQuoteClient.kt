package com.pavex.shipment.client

import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.shipment.exception.ShipmentGoneException
import com.pavex.shipment.exception.ShipmentUnavailableException
import com.pavex.shipment.security.service.ServiceJwtIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

@Component
class PricingQuoteClient(
    @Qualifier("shipmentLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${shipment.integrations.pricing.service-url:http://pricing-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun getValid(quoteId: UUID): PricingQuoteSnapshot {
        val response = try {
            client.get()
                .uri("/internal/v1/pricing/quotes/{quoteId}", quoteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForPricing()}")
                .retrieve()
                .body(PricingQuoteSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw ShipmentGoneException(
                "quote_invalid_or_expired",
                "The selected pricing quote is missing or expired.",
            )
        } catch (_: HttpClientErrorException.Gone) {
            throw ShipmentGoneException(
                "quote_invalid_or_expired",
                "The selected pricing quote is missing or expired.",
            )
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("pricing_authentication_failed", "Shipment could not authenticate with Pricing.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("pricing_authorization_failed", "Shipment is not authorized to read pricing quotes.")
        } catch (_: RestClientException) {
            throw unavailable("pricing_unavailable", "Pricing is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("pricing_invalid_response", "Pricing returned an empty quote response.")
    }

    private fun unavailable(code: String, message: String) = ShipmentUnavailableException(code, message)
}
