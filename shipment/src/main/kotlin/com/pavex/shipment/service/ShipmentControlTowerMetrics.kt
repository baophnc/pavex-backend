package com.pavex.shipment.service

import com.pavex.shipment.dto.ShipmentControlAlertSeverity
import com.pavex.shipment.dto.ShipmentControlAlertType
import com.pavex.shipment.dto.ShipmentControlAlertResponse
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class ShipmentControlTowerMetrics(registry: MeterRegistry) {
    private val total = AtomicLong()
    private val critical = AtomicLong()
    private val stuck = AtomicLong()
    private val overdueCases = AtomicLong()

    init {
        registry.gauge("pavex.shipment.control_tower.alerts.total", total)
        registry.gauge("pavex.shipment.control_tower.alerts.critical", critical)
        registry.gauge("pavex.shipment.control_tower.alerts.stuck", stuck)
        registry.gauge("pavex.shipment.control_tower.cases.overdue", overdueCases)
    }

    fun update(alerts: Collection<ShipmentControlAlertResponse>) {
        total.set(alerts.size.toLong())
        critical.set(alerts.count { it.severity == ShipmentControlAlertSeverity.CRITICAL }.toLong())
        stuck.set(alerts.count { it.type == ShipmentControlAlertType.SHIPMENT_STUCK }.toLong())
        overdueCases.set(alerts.count { it.type == ShipmentControlAlertType.CASE_OVERDUE }.toLong())
    }
}
