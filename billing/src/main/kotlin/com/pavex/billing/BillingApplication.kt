package com.pavex.billing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableScheduling
class BillingApplication

fun main(args: Array<String>) {
    runApplication<BillingApplication>(*args)
}
