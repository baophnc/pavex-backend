package com.pavex.fleet.client

import com.pavex.contracts.fleet.FleetHubScopeSnapshot
import com.pavex.contracts.fleet.FleetRouteLegSnapshot
import com.pavex.contracts.fleet.FleetRouteLegsRequest
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.fleet.exception.FleetUnavailableException
import com.pavex.fleet.security.ServiceJwtIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/** Driver line-haul work and viewer hub memberships, both owned by Operations. */
@Component
class OperationsFleetClient(
    @Qualifier("fleetLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${fleet.integrations.operations.service-url:http://operations-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun lineHaulAssignments(userId: UUID): List<WorkforceAssignmentSnapshot> = call {
        client.get()
            .uri("/internal/v1/operations/fleet/users/{userId}/line-haul-assignments", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")
            .retrieve()
            .body(object : ParameterizedTypeReference<List<WorkforceAssignmentSnapshot>>() {})
            .orEmpty()
    }

    fun hubIds(userId: UUID): Set<UUID> = call {
        client.get()
            .uri("/internal/v1/operations/fleet/users/{userId}/hub-scope", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")
            .retrieve()
            .body(FleetHubScopeSnapshot::class.java)
            ?.hubIds
            .orEmpty()
            .toSet()
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (_: RestClientException) {
        throw FleetUnavailableException("operations_unavailable", "Operations is temporarily unavailable. Please try again.")
    }
}

/** Lane, hubs and planned times of reserved route legs, owned by Network. */
@Component
class NetworkFleetClient(
    @Qualifier("fleetLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${fleet.integrations.network.service-url:http://network-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun routeLegs(routeLegIds: Collection<UUID>): List<FleetRouteLegSnapshot> {
        if (routeLegIds.isEmpty()) return emptyList()
        return try {
            client.post()
                .uri("/internal/v1/network/fleet/route-legs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
                .body(FleetRouteLegsRequest(routeLegIds.distinct()))
                .retrieve()
                .body(object : ParameterizedTypeReference<List<FleetRouteLegSnapshot>>() {})
                .orEmpty()
        } catch (_: RestClientException) {
            throw FleetUnavailableException("network_unavailable", "Network is temporarily unavailable. Please try again.")
        }
    }
}
