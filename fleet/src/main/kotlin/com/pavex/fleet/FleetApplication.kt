package com.pavex.fleet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableScheduling
class FleetApplication

fun main(args: Array<String>) {
    runApplication<FleetApplication>(*args)
}
