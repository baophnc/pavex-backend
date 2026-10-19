package com.pavex.shipment.client

import com.pavex.common.api.ApiErrorResponse
import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.shipment.exception.InvalidShipmentRequestException
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
import java.math.BigDecimal
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

    /**
     * Prices one order the way the shop's own form does, through Pricing's public quote endpoint (it needs no token).
     * A refusal (unknown ward, over the weight or COD limit, ...) comes back as [InvalidShipmentRequestException]
     * carrying Pricing's code and message.
     */
    fun quote(request: PricingQuoteRequest): PricingQuoteResult {
        val response = try {
            client.post()
                .uri("/api/v1/pricing/quotes")
                .body(request)
                .retrieve()
                .body(PricingQuoteResult::class.java)
        } catch (exception: HttpClientErrorException) {
            val error = runCatching { exception.getResponseBodyAs(ApiErrorResponse::class.java) }.getOrNull()
            throw InvalidShipmentRequestException(
                error?.code ?: "quote_rejected",
                error?.message ?: "Pricing could not price this order.",
            )
        } catch (_: RestClientException) {
            throw unavailable("pricing_unavailable", "Pricing is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("pricing_invalid_response", "Pricing returned an empty quote response.")
    }

    private fun unavailable(code: String, message: String) = ShipmentUnavailableException(code, message)
}

data class PricingQuoteRequest(
    val originProvinceCode: String,
    val originWardCode: String,
    val destinationProvinceCode: String,
    val destinationWardCode: String,
    val codAmount: Long,
    val declaredValue: Long,
    val parcels: List<PricingQuoteParcel>,
)

data class PricingQuoteParcel(
    val weightKg: BigDecimal,
    val lengthCm: BigDecimal,
    val widthCm: BigDecimal,
    val heightCm: BigDecimal,
)

/** The part of Pricing's quote response that bulk ordering needs: one option per service level. */
data class PricingQuoteResult(
    val options: List<PricingQuoteOption>,
)

data class PricingQuoteOption(
    val quoteId: UUID,
    val serviceLevel: String,
    val total: Long,
)
