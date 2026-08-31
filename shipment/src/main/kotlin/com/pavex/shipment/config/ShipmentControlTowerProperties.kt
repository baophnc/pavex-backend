package com.pavex.shipment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
@ConfigurationProperties(prefix = "shipment.control-tower")
class ShipmentControlTowerProperties {
    var sentNotArrivedAfter: Duration = Duration.ofHours(4)
    var missingArrivalScanGrace: Duration = Duration.ofMinutes(20)
    var arrivedNotOutForDeliveryAfter: Duration = Duration.ofHours(2)
    var shipmentStuckAfter: Duration = Duration.ofHours(12)
    var criticalOverdueAfter: Duration = Duration.ofHours(4)
    var routeDeviationLookback: Duration = Duration.ofHours(24)
    var deliveryProofLookback: Duration = Duration.ofDays(2)
    var lowCaseSla: Duration = Duration.ofHours(24)
    var mediumCaseSla: Duration = Duration.ofHours(8)
    var highCaseSla: Duration = Duration.ofHours(2)
    var criticalCaseSla: Duration = Duration.ofMinutes(30)
    var requireSignatureOnDelivery: Boolean = false
}
