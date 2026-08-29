package com.pavex.shipment.config

import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class RestClientConfig {
    @Bean
    @Primary
    fun plainRestClientBuilder(): RestClient.Builder = RestClient.builder()

    @Bean("shipmentLoadBalancedRestClientBuilder")
    @LoadBalanced
    fun shipmentLoadBalancedRestClientBuilder(): RestClient.Builder = RestClient.builder()
}
