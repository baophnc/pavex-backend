package com.pavex.identity.config

import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class RestClientConfig {

    /**
     * Keep a plain builder as the primary candidate. Eureka itself uses a
     * RestClient internally to call the registry URL (localhost in local dev);
     * if the only builder is @LoadBalanced, that URL is incorrectly treated as
     * a service id and Eureka registration/heartbeats fail.
     */
    @Bean
    @Primary
    fun plainRestClientBuilder(): RestClient.Builder =
        RestClient.builder()

    /**
     * Business service-to-service calls explicitly request this builder with
     * @LoadBalanced so names such as http://identity-service are resolved via
     * Eureka + Spring Cloud LoadBalancer.
     */
    @Bean
    @LoadBalanced
    fun loadBalancedRestClientBuilder(): RestClient.Builder =
        RestClient.builder()
}
