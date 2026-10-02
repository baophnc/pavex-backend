package com.pavex.notifications.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun notificationsOpenApi(
        @Value("\${pavex.openapi.gateway-url:http://localhost:8080}") gatewayUrl: String,
    ): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Pavex Notifications API")
                .version("v1")
                .description("In-app notifications: the inbox, and messages between hub staff, couriers, drivers and shops."),
        )
        .servers(listOf(Server().url(gatewayUrl)))
}
