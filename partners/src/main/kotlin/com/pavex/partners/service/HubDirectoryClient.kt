package com.pavex.partners.service

import com.pavex.contracts.network.NetworkLocationResolutionRequest
import com.pavex.contracts.network.NetworkServingHubResponse
import com.pavex.contracts.operations.ShipmentCaseAdminScopeSnapshot
import com.pavex.partners.exception.ApiException
import com.pavex.partners.security.service.ServiceJwtIssuer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/** Which hub serves a ward (Network) and which hubs a user works at (Operations). */
@Component
class HubDirectoryClient(
    @LoadBalanced builder: RestClient.Builder,
    @Value("\${partners.network.service-url:http://network-service}") networkUrl: String,
    @Value("\${partners.operations.service-url:http://operations-service}") operationsUrl: String,
    private val serviceJwt: ServiceJwtIssuer,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val network = builder.clone().baseUrl(networkUrl).build()
    private val operations = builder.clone().baseUrl(operationsUrl).build()

    /** The hub serving [wardCode], or null when Network has no coverage there or cannot be reached. */
    fun servingHub(provinceCode: String, wardCode: String): UUID? = try {
        network.post()
            .uri("/internal/v1/network/partners/serving-hub")
            .headers { it.setBearerAuth(serviceJwt.issueForNetwork()) }
            .body(NetworkLocationResolutionRequest(provinceCode, wardCode))
            .retrieve()
            .body(NetworkServingHubResponse::class.java)
            ?.hubId
    } catch (exception: RestClientException) {
        log.warn("Serving hub of ward {} not resolved: {}", wardCode, exception.message)
        null
    }

    /** The hubs [userId] works at; a hub-scoped list cannot be shown without them. */
    fun hubsOf(userId: UUID): Set<UUID> = try {
        operations.get()
            .uri("/internal/v1/operations/partners/hub-scope/{userId}", userId)
            .headers { it.setBearerAuth(serviceJwt.issueForOperations()) }
            .retrieve()
            .body(ShipmentCaseAdminScopeSnapshot::class.java)
            ?.hubIds
            .orEmpty()
    } catch (exception: RestClientException) {
        throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "hub_scope_unavailable", "Your hubs could not be loaded. Try again.")
    }
}
