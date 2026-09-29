package com.pavex.billing.client

import com.pavex.billing.exception.BillingUnavailableException
import com.pavex.billing.security.ServiceJwtIssuer
import com.pavex.contracts.operations.OperationsAccessCheckRequest
import com.pavex.contracts.operations.OperationsAccessCheckResponse
import com.pavex.contracts.operations.OperationsCapability
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/** Asks Operations whether an active workforce member may act at a hub. */
@Component
class OperationsClient(
    @Qualifier("billingLoadBalancedRestClientBuilder") builder: RestClient.Builder,
    private val serviceJwtIssuer: ServiceJwtIssuer,
    @Value("\${billing.integrations.operations.service-url:http://operations-service}") serviceUrl: String,
) {
    private val client = builder.baseUrl(serviceUrl).build()

    fun canOperateAtHub(userId: UUID, hubId: UUID, capability: OperationsCapability): Boolean {
        val response = try {
            client.post()
                .uri("/internal/v1/operations/access/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${serviceJwtIssuer.issueForOperations()}")
                .body(OperationsAccessCheckRequest(userId, hubId, capability))
                .retrieve()
                .body(OperationsAccessCheckResponse::class.java)
        } catch (_: RestClientException) {
            throw BillingUnavailableException("operations_unavailable", "Operations is temporarily unavailable. Please try again.")
        }
        return response?.allowed == true
    }
}
