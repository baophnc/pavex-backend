package com.pavex.pricing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

@SpringBootApplication
@EnableDiscoveryClient
class PricingApplication

fun main(args: Array<String>) {
    runApplication<PricingApplication>(*args)
}
