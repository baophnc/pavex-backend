package com.pavex.shipment.client

import com.pavex.contracts.operations.CancelShipmentOperationsRequest
import com.pavex.contracts.operations.OperationsAccessCheckRequest
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.OperationsCapability
import com.pavex.contracts.operations.PlanShipmentOperationsRequest
import com.pavex.contracts.operations.ShipmentAssignmentAccessRequest
import com.pavex.contracts.operations.ShipmentAssignmentCompletionRequest
import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.contracts.operations.ShipmentOperationsAssignmentSnapshot
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.contracts.operations.ShipmentOperationsPlanResponse
import com.pavex.contracts.operations.ShipmentOperationsRouteLeg
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.exception.ShipmentUnavailableException
import com.pavex.shipment.security.service.ServiceJwtIssuer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

@Component
class OperationsShipmentClient(
    @Qualifier("shipmentLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${shipment.integrations.operations.service-url:http://operations-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun plan(
        shipmentId: UUID,
        reservation: ShipmentNetworkReservationSnapshot,
        startsAtOriginHub: Boolean = false,
    ): ShipmentOperationsPlanResponse {
        val response = try {
            client.post()
                .uri("/internal/v1/operations/shipments/{shipmentId}/plan", shipmentId)
                .serviceAuth()
                .body(
                    PlanShipmentOperationsRequest(
                        originHubId = reservation.origin.hubId,
                        destinationHubId = reservation.destination.hubId,
                        routeLegs = reservation.routeLegs.map {
                            ShipmentOperationsRouteLeg(it.routeLegId, it.sequence, it.fromHubId, it.toHubId)
                        },
                        startsAtOriginHub = startsAtOriginHub,
                    ),
                )
                .retrieve()
                .body(ShipmentOperationsPlanResponse::class.java)
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("invalid_operations_plan", "Operations rejected the shipment assignment plan.")
        } catch (_: HttpClientErrorException.Conflict) {
            throw ShipmentConflictException("operations_plan_conflict", "Operations could not create a consistent assignment plan.")
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response ?: throw ShipmentUnavailableException(
            "operations_invalid_response",
            "Operations returned an empty assignment plan response.",
        )
    }

    fun cancel(shipmentId: UUID, reason: String) {
        try {
            client.post()
                .uri("/internal/v1/operations/shipments/{shipmentId}/cancel", shipmentId)
                .serviceAuth()
                .body(CancelShipmentOperationsRequest(reason))
                .retrieve()
                .toBodilessEntity()
        } catch (_: HttpClientErrorException.Conflict) {
            throw ShipmentConflictException(
                "operations_assignment_conflict",
                "Operations could not cancel the shipment assignments in their current state.",
            )
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
    }

    fun cancelBestEffort(shipmentId: UUID, reason: String) {
        try {
            cancel(shipmentId, reason)
        } catch (exception: RuntimeException) {
            logger.error("Failed to compensate Operations assignment plan for shipment {}", shipmentId, exception)
        }
    }

    fun caseAdminScope(userId: UUID): ShipmentCaseAdminScopeSnapshot {
        val response = try {
            client.get()
                .uri("/internal/v1/operations/shipments/case-admin-scope/{userId}", userId)
                .serviceAuthHeaders()
                .retrieve()
                .body(ShipmentCaseAdminScopeSnapshot::class.java)
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response ?: throw ShipmentUnavailableException(
            "operations_invalid_response",
            "Operations returned an empty case-admin scope response.",
        )
    }

    fun canOperateAtHub(userId: UUID, hubId: UUID, capability: OperationsCapability): Boolean {
        val response = try {
            client.post()
                .uri("/internal/v1/operations/shipments/access/check")
                .serviceAuth()
                .body(OperationsAccessCheckRequest(userId, hubId, capability))
                .retrieve()
                .body(com.pavex.contracts.operations.OperationsAccessCheckResponse::class.java)
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response?.allowed == true
    }

    fun authorizeAssignment(
        shipmentId: UUID,
        assignmentId: UUID,
        actorUserId: UUID,
        expectedType: OperationsAssignmentType,
        hubId: UUID,
        routeLegId: UUID? = null,
    ): ShipmentOperationsAssignmentSnapshot {
        val response = try {
            client.post()
                .uri(
                    "/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/authorize",
                    shipmentId,
                    assignmentId,
                )
                .serviceAuth()
                .body(ShipmentAssignmentAccessRequest(actorUserId, expectedType, hubId, routeLegId))
                .retrieve()
                .body(ShipmentOperationsAssignmentSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw ShipmentForbiddenException("assignment_forbidden", "The supplied operational assignment is not owned by this user.")
        } catch (_: HttpClientErrorException.Conflict) {
            throw ShipmentConflictException("assignment_not_ready", "The operational assignment is not ready for this shipment action.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("assignment_invalid", "The operational assignment reference is invalid.")
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response ?: throw ShipmentUnavailableException("operations_invalid_response", "Operations returned an empty assignment response.")
    }

    /** Hands hub handling to the hub staff member scanning the parcel when nobody accepted it yet. */
    fun claimHubHandling(
        shipmentId: UUID,
        assignmentId: UUID,
        actorUserId: UUID,
        expectedType: OperationsAssignmentType,
        hubId: UUID,
        routeLegId: UUID? = null,
    ): ShipmentOperationsAssignmentSnapshot {
        val response = try {
            client.post()
                .uri(
                    "/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/claim-handling",
                    shipmentId,
                    assignmentId,
                )
                .serviceAuth()
                .body(ShipmentAssignmentAccessRequest(actorUserId, expectedType, hubId, routeLegId))
                .retrieve()
                .body(ShipmentOperationsAssignmentSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw ShipmentForbiddenException("scan_hub_forbidden", "The current user cannot handle parcels at this hub.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw ShipmentForbiddenException("scan_hub_forbidden", "The current user cannot handle parcels at this hub.")
        } catch (_: HttpClientErrorException.Conflict) {
            throw ShipmentConflictException("assignment_not_ready", "Hub handling for this shipment is not open yet.")
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response ?: throw ShipmentUnavailableException("operations_invalid_response", "Operations returned an empty assignment response.")
    }

    fun completeAssignment(
        shipmentId: UUID,
        assignmentId: UUID,
        actorUserId: UUID?,
        expectedType: OperationsAssignmentType,
        hubId: UUID,
        routeLegId: UUID? = null,
        note: String? = null,
    ): ShipmentOperationsAssignmentSnapshot {
        val response = try {
            client.post()
                .uri(
                    "/internal/v1/operations/shipments/{shipmentId}/assignments/{assignmentId}/complete",
                    shipmentId,
                    assignmentId,
                )
                .serviceAuth()
                .body(ShipmentAssignmentCompletionRequest(actorUserId, expectedType, hubId, routeLegId, note))
                .retrieve()
                .body(ShipmentOperationsAssignmentSnapshot::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            throw ShipmentConflictException("assignment_missing", "The expected operational assignment was not found.")
        } catch (_: HttpClientErrorException.Conflict) {
            throw ShipmentConflictException("assignment_completion_conflict", "The operational assignment cannot be completed yet.")
        } catch (_: HttpClientErrorException.BadRequest) {
            throw InvalidShipmentRequestException("assignment_invalid", "The operational assignment completion reference is invalid.")
        } catch (exception: RestClientException) {
            throw unavailable(exception)
        }
        return response ?: throw ShipmentUnavailableException("operations_invalid_response", "Operations returned an empty completion response.")
    }

    fun workforceAssignments(userId: UUID): List<WorkforceAssignmentSnapshot> = try {
        client.get()
            .uri("/internal/v1/operations/shipments/assignments/by-user/{userId}", userId)
            .serviceAuthHeaders()
            .retrieve()
            .body(object : ParameterizedTypeReference<List<WorkforceAssignmentSnapshot>>() {})
            .orEmpty()
    } catch (exception: RestClientException) {
        throw unavailable(exception)
    }

    fun findRouteLegAssignment(shipmentId: UUID, routeLegId: UUID): ShipmentOperationsAssignmentSnapshot? = try {
        client.get()
            .uri(
                "/internal/v1/operations/shipments/{shipmentId}/route-legs/{routeLegId}/assignment",
                shipmentId,
                routeLegId,
            )
            .serviceAuthHeaders()
            .retrieve()
            .body(ShipmentOperationsAssignmentSnapshot::class.java)
    } catch (_: HttpClientErrorException.NotFound) {
        null
    } catch (exception: RestClientException) {
        throw unavailable(exception)
    }

    fun findTypeAssignment(
        shipmentId: UUID,
        type: OperationsAssignmentType,
        hubId: UUID,
    ): ShipmentOperationsAssignmentSnapshot? = try {
        client.get()
            .uri(
                "/internal/v1/operations/shipments/{shipmentId}/types/{type}/hubs/{hubId}/assignment",
                shipmentId,
                type.name,
                hubId,
            )
            .serviceAuthHeaders()
            .retrieve()
            .body(ShipmentOperationsAssignmentSnapshot::class.java)
    } catch (_: HttpClientErrorException.NotFound) {
        null
    } catch (exception: RestClientException) {
        throw unavailable(exception)
    }

    private fun RestClient.RequestBodySpec.serviceAuth(): RestClient.RequestBodySpec =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")

    private fun RestClient.RequestHeadersSpec<*>.serviceAuthHeaders(): RestClient.RequestHeadersSpec<*> =
        header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")

    private fun unavailable(exception: RestClientException): ShipmentUnavailableException = ShipmentUnavailableException(
        "operations_unavailable",
        "Operations is temporarily unavailable. Please retry this shipment action.",
    ).also { it.addSuppressed(exception) }

    companion object {
        private val logger = LoggerFactory.getLogger(OperationsShipmentClient::class.java)
    }
}
