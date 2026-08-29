package com.pavex.shipment.client

import com.pavex.contracts.partners.MerchantOrderCapabilitySnapshot
import com.pavex.contracts.partners.PartnerLocationResolutionRequest
import com.pavex.contracts.partners.PartnerLocationSnapshot
import com.pavex.shipment.exception.InvalidShipmentRequestException
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
class PartnerShipmentClient(
    @Qualifier("shipmentLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${shipment.integrations.partners.service-url:http://partners-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun orderCapability(userId: UUID): MerchantOrderCapabilitySnapshot = call(
        operation = "read merchant order capability",
    ) {
        client.get()
            .uri("/internal/v1/merchants/by-user/{userId}/order-capability", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForPartners()}")
            .retrieve()
            .body(MerchantOrderCapabilitySnapshot::class.java)
    } ?: throw unavailable("partners_invalid_response", "Partners returned an empty merchant capability response.")

    fun resolveLocation(provinceCode: String, wardCode: String): PartnerLocationSnapshot {
        val response = try {
            client.post()
                .uri("/internal/v1/locations/resolve")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForPartners()}")
                .body(PartnerLocationResolutionRequest(provinceCode.trim(), wardCode.trim()))
                .retrieve()
                .body(PartnerLocationSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw InvalidShipmentRequestException("invalid_address", "Province or ward was not found.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("invalid_address", "Ward does not belong to the selected province.")
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("partners_authentication_failed", "Shipment could not authenticate with Partners.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("partners_authorization_failed", "Shipment is not authorized to resolve partner locations.")
        } catch (_: RestClientException) {
            throw unavailable("partners_unavailable", "Partners is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("partners_invalid_response", "Partners returned an empty location response.")
    }

    private fun <T> call(operation: String, block: () -> T?): T? = try {
        block()
    } catch (_: HttpClientErrorException.Unauthorized) {
        throw unavailable("partners_authentication_failed", "Shipment could not authenticate with Partners while trying to $operation.")
    } catch (_: HttpClientErrorException.Forbidden) {
        throw unavailable("partners_authorization_failed", "Shipment is not authorized to $operation.")
    } catch (_: RestClientException) {
        throw unavailable("partners_unavailable", "Partners is temporarily unavailable. Please try again.")
    }

    private fun unavailable(code: String, message: String) = ShipmentUnavailableException(code, message)
}
