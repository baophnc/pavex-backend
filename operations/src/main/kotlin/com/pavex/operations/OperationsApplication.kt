package com.pavex.operations

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.discovery.EnableDiscoveryClient

@SpringBootApplication
@EnableDiscoveryClient
class OperationsApplication

fun main(args: Array<String>) {
    runApplication<OperationsApplication>(*args)
}
