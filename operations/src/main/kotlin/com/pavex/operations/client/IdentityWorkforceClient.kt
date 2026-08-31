package com.pavex.operations.client

import com.pavex.contracts.operations.WorkforceIdentitySnapshot
import com.pavex.contracts.operations.WorkforceIdentitySnapshotRequest
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
class IdentityWorkforceClient(
    @Qualifier("operationsLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${operations.integrations.identity.service-url:http://identity-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun snapshots(userIds: Collection<UUID>, search: String? = null): List<WorkforceIdentitySnapshot> =
        userIds.asSequence()
            .distinct()
            .chunked(BATCH_SIZE)
            .flatMap { chunk -> fetch(chunk, search) }
            .toList()

    private fun fetch(userIds: List<UUID>, search: String?): List<WorkforceIdentitySnapshot> = try {
        client.post()
            .uri("/internal/v1/operations/identities/snapshots")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForIdentity()}")
            .body(WorkforceIdentitySnapshotRequest(userIds = userIds, search = search))
            .retrieve()
            .body(object : ParameterizedTypeReference<List<WorkforceIdentitySnapshot>>() {})
            .orEmpty()
    } catch (_: HttpClientErrorException.Unauthorized) {
        throw unavailable("identity_authentication_failed", "Operations could not authenticate with Identity.")
    } catch (_: HttpClientErrorException.Forbidden) {
        throw unavailable("identity_authorization_failed", "Operations is not authorized to read workforce Identity snapshots.")
    } catch (_: RestClientException) {
        throw unavailable("identity_unavailable", "Identity workforce lookup is temporarily unavailable.")
    }

    private fun unavailable(code: String, message: String) = OperationsUnavailableException(code, message)

    companion object {
        private const val BATCH_SIZE = 500
    }
}
