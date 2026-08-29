package com.pavex.shipment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

@SpringBootApplication
@EnableDiscoveryClient
class ShipmentApplication

fun main(args: Array<String>) {
    runApplication<ShipmentApplication>(*args)
}
