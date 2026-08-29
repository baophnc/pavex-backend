package com.pavex.shipment.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun shipmentOpenApi(
        @Value("\${pavex.openapi.gateway-url:http://localhost:8080}") gatewayUrl: String,
    ): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Pavex Shipment API")
                .version("v1")
                .description("Shipment, parcel and operational shipment workflow APIs."),
        )
        .servers(listOf(Server().url(gatewayUrl)))
}
