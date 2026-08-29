package com.pavex.pricing.client

import com.pavex.contracts.network.NetworkLocationResolutionRequest
import com.pavex.contracts.network.NetworkLocationResolutionResponse
import com.pavex.pricing.exception.InvalidPricingRequestException
import com.pavex.pricing.exception.PricingUnavailableException
import com.pavex.pricing.exception.UnprocessablePricingRequestException
import com.pavex.pricing.security.service.ServiceJwtIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Component
class NetworkLocationClient(
    @Qualifier("pricingLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${pricing.network.service-url:http://network-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun resolve(provinceCode: String, wardCode: String): NetworkLocationResolutionResponse {
        val response = try {
            client.post()
                .uri("/internal/v1/network/locations/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .body(NetworkLocationResolutionRequest(provinceCode.trim(), wardCode.trim()))
                .retrieve()
                .body(NetworkLocationResolutionResponse::class.java)
        } catch (exception: HttpClientErrorException.NotFound) {
            throw UnprocessablePricingRequestException(
                "location_not_covered",
                "Pavex does not currently cover one of the supplied locations.",
            )
        } catch (exception: HttpClientErrorException.BadRequest) {
            throw InvalidPricingRequestException(
                "invalid_location",
                "Province and ward codes must refer to the same valid location.",
            )
        } catch (exception: HttpClientErrorException.Unauthorized) {
            throw PricingUnavailableException(
                "network_authentication_failed",
                "Pricing could not authenticate with the network service.",
            )
        } catch (exception: HttpClientErrorException.Forbidden) {
            throw PricingUnavailableException(
                "network_authorization_failed",
                "Pricing is not authorized to resolve network coverage.",
            )
        } catch (exception: RestClientException) {
            throw PricingUnavailableException(
                "network_unavailable",
                "Network coverage is temporarily unavailable. Please try again.",
            )
        }

        return response ?: throw PricingUnavailableException(
            "network_invalid_response",
            "Network coverage returned an empty response.",
        )
    }
}
