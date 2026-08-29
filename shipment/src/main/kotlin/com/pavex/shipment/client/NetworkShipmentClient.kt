package com.pavex.shipment.client

import com.pavex.contracts.network.ReleaseShipmentNetworkRequest
import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentUnavailableException
import com.pavex.shipment.exception.UnprocessableShipmentException
import com.pavex.shipment.security.service.ServiceJwtIssuer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

@Component
class NetworkShipmentClient(
    @Qualifier("shipmentLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${shipment.integrations.network.service-url:http://network-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun reserve(request: ReserveShipmentNetworkRequest): ShipmentNetworkReservationSnapshot {
        val response = try {
            client.post()
                .uri("/internal/v1/network/shipments/capacity/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .body(request)
                .retrieve()
                .body(ShipmentNetworkReservationSnapshot::class.java)
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("invalid_network_request", "Shipment routing inputs are invalid.")
        } catch (_: HttpClientErrorException.NotFound) {
            throw routeUnavailable()
        } catch (_: HttpClientErrorException.Conflict) {
            throw routeUnavailable()
        } catch (_: HttpClientErrorException.UnprocessableEntity) {
            throw routeUnavailable()
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("network_authentication_failed", "Shipment could not authenticate with Network.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("network_authorization_failed", "Shipment is not authorized to reserve network capacity.")
        } catch (_: RestClientException) {
            throw unavailable("network_unavailable", "Network routing is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("network_invalid_response", "Network returned an empty reservation response.")
    }

    fun releaseBestEffort(shipmentId: UUID, routeRevision: Int, actorUserId: UUID, reason: String) {
        try {
            client.post()
                .uri(
                    "/internal/v1/network/shipments/capacity/reservations/{shipmentId}/revisions/{routeRevision}/release",
                    shipmentId,
                    routeRevision,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .body(ReleaseShipmentNetworkRequest(actorUserId, reason))
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientException) {
            logger.error(
                "Failed to compensate network capacity reservation for shipment {} revision {}",
                shipmentId,
                routeRevision,
                exception,
            )
        }
    }

    private fun routeUnavailable() = UnprocessableShipmentException(
        "network_route_unavailable",
        "No active network route with enough capacity is currently available for this shipment. Request a new quote or try again later.",
    )

    private fun unavailable(code: String, message: String) = ShipmentUnavailableException(code, message)

    companion object {
        private val logger = LoggerFactory.getLogger(NetworkShipmentClient::class.java)
    }
}
