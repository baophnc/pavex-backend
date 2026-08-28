package com.pavex.partners

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class PartnersApplication

fun main(args: Array<String>) {
    runApplication<PartnersApplication>(*args)
}
