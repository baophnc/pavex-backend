package com.pavex.operations.client

import com.pavex.contracts.operations.OperationsHubSnapshot
import com.pavex.contracts.operations.OperationsHubSnapshotRequest
import com.pavex.operations.exception.OperationsUnavailableException
import com.pavex.operations.security.service.ServiceJwtIssuer
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
class NetworkHubClient(
    @Qualifier("operationsLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${operations.integrations.network.service-url:http://network-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun snapshots(hubIds: Collection<UUID>): List<OperationsHubSnapshot> =
        hubIds.asSequence()
            .distinct()
            .chunked(BATCH_SIZE)
            .flatMap(::fetch)
            .toList()

    fun find(hubId: UUID): OperationsHubSnapshot? = snapshots(listOf(hubId)).singleOrNull()

    private fun fetch(hubIds: List<UUID>): List<OperationsHubSnapshot> = try {
        client.post()
            .uri("/internal/v1/network/operations/hubs/snapshots")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForNetwork()}")
            .body(OperationsHubSnapshotRequest(hubIds))
            .retrieve()
            .body(object : ParameterizedTypeReference<List<OperationsHubSnapshot>>() {})
            .orEmpty()
    } catch (_: HttpClientErrorException.Unauthorized) {
        throw unavailable("network_authentication_failed", "Operations could not authenticate with Network.")
    } catch (_: HttpClientErrorException.Forbidden) {
        throw unavailable("network_authorization_failed", "Operations is not authorized to read Network hubs.")
    } catch (_: RestClientException) {
        throw unavailable("network_unavailable", "Network hub lookup is temporarily unavailable.")
    }

    private fun unavailable(code: String, message: String) = OperationsUnavailableException(code, message)

    companion object {
        private const val BATCH_SIZE = 500
    }
}
