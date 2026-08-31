package com.pavex.operations.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun operationsOpenApi(
        @Value("\${pavex.openapi.gateway-url:http://localhost:8080}") gatewayUrl: String,
    ): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Pavex Operations API")
                .version("v1")
                .description("Operations workforce, shifts, assignments and dispatch APIs."),
        )
        .servers(listOf(Server().url(gatewayUrl)))
}
