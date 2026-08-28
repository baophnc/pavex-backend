package com.pavex.partners.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    fun partnersOpenApi(
        @Value("\${pavex.openapi.gateway-url:http://localhost:8080}")
        gatewayUrl: String,
    ): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Pavex Partners API")
                    .description(
                        "Merchant, pickup address, customer address and Vietnam location APIs.",
                    )
                    .version("v1"),
            )
            .servers(
                listOf(
                    Server()
                        .url(gatewayUrl)
                        .description("Pavex API Gateway"),
                ),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_AUTH,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Pavex user access token"),
                    ),
            )

    companion object {
        const val BEARER_AUTH = "bearerAuth"
    }
}
