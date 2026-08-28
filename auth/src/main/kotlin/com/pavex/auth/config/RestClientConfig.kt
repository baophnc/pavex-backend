package com.pavex.auth.config

import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration(proxyBeanMethods = false)
class RestClientConfig {

    @Bean
    @LoadBalanced
    fun loadBalancedRestClientBuilder(): RestClient.Builder =
        RestClient.builder()
}
