package com.pavex.fleet.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("fleet.tracking")
data class FleetTrackingProperties(
    val signalLostAfter: Duration = Duration.ofMinutes(5),
    val retentionDays: Int = 30,
    val arrivalCheckInterval: Duration = Duration.ofMinutes(1),
)
