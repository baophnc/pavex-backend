package com.pavex.shipment.client

import com.pavex.contracts.network.ConfirmShipmentNetworkRequest
import com.pavex.contracts.network.ReleaseShipmentNetworkRequest
import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentHubSnapshot
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.contracts.network.ShipmentOperationalHubSnapshot
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

    fun release(shipmentId: UUID, routeRevision: Int, actorUserId: UUID, reason: String) {
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
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("network_authentication_failed", "Shipment could not authenticate with Network.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("network_authorization_failed", "Shipment is not authorized to release network capacity.")
        } catch (_: HttpClientErrorException.NotFound) {
            throw unavailable(
                "network_reservation_missing",
                "The shipment capacity reservation could not be found in Network.",
            )
        } catch (_: RestClientException) {
            throw unavailable(
                "network_unavailable",
                "Network capacity release is temporarily unavailable. Retry the cancellation request.",
            )
        }
    }

    fun resolveHub(hubId: UUID): ShipmentHubSnapshot {
        val response = try {
            client.get()
                .uri("/internal/v1/network/shipments/hubs/{hubId}", hubId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .retrieve()
                .body(ShipmentHubSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw InvalidShipmentRequestException("case_hub_not_found", "The case hub does not exist in Network.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("case_hub_invalid", "The case hub reference is invalid.")
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("network_authentication_failed", "Shipment could not authenticate with Network.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("network_authorization_failed", "Shipment is not authorized to resolve Network hubs.")
        } catch (_: RestClientException) {
            throw unavailable("network_unavailable", "Network hub validation is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("network_invalid_response", "Network returned an empty hub response.")
    }

    fun resolveOperationalHub(
        shipmentId: UUID,
        routeRevision: Int,
        hubId: UUID,
    ): ShipmentOperationalHubSnapshot {
        val response = try {
            client.get()
                .uri(
                    "/internal/v1/network/shipments/routes/{shipmentId}/revisions/{routeRevision}/hubs/{hubId}",
                    shipmentId,
                    routeRevision,
                    hubId,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .retrieve()
                .body(ShipmentOperationalHubSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw InvalidShipmentRequestException("hub_not_found", "The scan hub does not exist in Network.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("invalid_scan_hub", "The scan hub request is invalid.")
        } catch (_: HttpClientErrorException.Unauthorized) {
            throw unavailable("network_authentication_failed", "Shipment could not authenticate with Network.")
        } catch (_: HttpClientErrorException.Forbidden) {
            throw unavailable("network_authorization_failed", "Shipment is not authorized to resolve Network hubs.")
        } catch (_: RestClientException) {
            throw unavailable("network_unavailable", "Network hub validation is temporarily unavailable. Please try again.")
        }
        return response ?: throw unavailable("network_invalid_response", "Network returned an empty hub response.")
    }

    /**
     * Tells Network the reservation now belongs to a persisted shipment so it is never expired.
     * Transport failures are thrown as [RestClientException] so the caller can retry later.
     */
    fun confirm(shipmentId: UUID, routeRevision: Int, actorUserId: UUID): NetworkConfirmationResult =
        try {
            client.post()
                .uri(
                    "/internal/v1/network/shipments/capacity/reservations/{shipmentId}/revisions/{routeRevision}/confirm",
                    shipmentId,
                    routeRevision,
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .body(ConfirmShipmentNetworkRequest(actorUserId))
                .retrieve()
                .toBodilessEntity()
            NetworkConfirmationResult.CONFIRMED
        } catch (_: HttpClientErrorException.Conflict) {
            NetworkConfirmationResult.ALREADY_RELEASED
        } catch (_: HttpClientErrorException.NotFound) {
            NetworkConfirmationResult.NOT_FOUND
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

enum class NetworkConfirmationResult { CONFIRMED, ALREADY_RELEASED, NOT_FOUND }
