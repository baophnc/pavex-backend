package com.pavex.notifications.client

import com.pavex.contracts.operations.HubPersonSnapshot
import com.pavex.contracts.operations.WorkPersonSnapshot
import com.pavex.notifications.exception.NotificationUnavailableException
import com.pavex.notifications.security.ServiceJwtIssuer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/** Who works where, owned by Operations. */
@Component
class OperationsPeopleClient(
    @Qualifier("notificationsLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${notifications.integrations.operations.service-url:http://operations-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun person(userId: UUID): WorkPersonSnapshot = call {
        client.get()
            .uri("/internal/v1/operations/notifications/people/{userId}", userId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")
            .retrieve()
            .body(WorkPersonSnapshot::class.java)
            ?: WorkPersonSnapshot(userId, null, emptyList())
    }

    fun hubPeople(hubId: UUID, kinds: Collection<String>): List<HubPersonSnapshot> = call {
        client.get()
            .uri { it.path("/internal/v1/operations/notifications/hubs/{hubId}/people").queryParam("kinds", kinds.joinToString(",")).build(hubId) }
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")
            .retrieve()
            .body(object : ParameterizedTypeReference<List<HubPersonSnapshot>>() {})
            .orEmpty()
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (exception: RestClientException) {
        throw NotificationUnavailableException("operations_unavailable", "Operations did not answer: ${exception.message}")
    }
}
