package com.pavex.operations.config

import com.pavex.security.TrustedServiceTokens
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder

/** The services that may call Operations' internal API, and the one scope each must carry. */
@Configuration
class InternalServiceJwtConfig(
    private val trusted: TrustedServiceTokens,
) {
    /** Notifications may only read who works where, to address hub messages and alerts. */
    @Bean("notificationsServiceJwtDecoder")
    fun notificationsServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("notifications", NOTIFICATIONS_SERVICE, OPERATIONS_SERVICE, OPERATIONS_PEOPLE_READ)

    /** Partners may only ask which hubs a user works at, to scope merchant lists. */
    @Bean("partnersServiceJwtDecoder")
    fun partnersServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("partners", PARTNERS_SERVICE, OPERATIONS_SERVICE, OPERATIONS_HUB_SCOPE_READ)

    /** Billing may only ask whether a user can handle cash at a hub. */
    @Bean("billingServiceJwtDecoder")
    fun billingServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("billing", BILLING_SERVICE, OPERATIONS_SERVICE, OPERATIONS_ACCESS_CHECK_SCOPE)

    /** Fleet may only read a driver's line-haul work and a viewer's hub memberships. */
    @Bean("fleetServiceJwtDecoder")
    fun fleetServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("fleet", FLEET_SERVICE, OPERATIONS_SERVICE, OPERATIONS_FLEET_READ_SCOPE)

    @Bean("shipmentServiceJwtDecoder")
    fun shipmentServiceJwtDecoder(): JwtDecoder =
        trusted.trustedService("shipment", SHIPMENT_SERVICE, OPERATIONS_SERVICE, OPERATIONS_SHIPMENT_SCOPE)

    companion object {
        private const val SHIPMENT_SERVICE = "shipment-service"
        private const val OPERATIONS_SERVICE = "operations-service"
        private const val OPERATIONS_SHIPMENT_SCOPE = "operations.shipments.execute"
        private const val BILLING_SERVICE = "billing-service"
        private const val OPERATIONS_ACCESS_CHECK_SCOPE = "operations.access.check"
        private const val FLEET_SERVICE = "fleet-service"
        private const val OPERATIONS_FLEET_READ_SCOPE = "operations.fleet.read"
        private const val PARTNERS_SERVICE = "partners-service"
        private const val OPERATIONS_HUB_SCOPE_READ = "operations.hub-scope.read"
        private const val NOTIFICATIONS_SERVICE = "notifications-service"
        private const val OPERATIONS_PEOPLE_READ = "operations.people.read"
    }
}
