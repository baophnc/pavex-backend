package com.pavex.shipment.dto

import java.time.Instant
import java.util.UUID

enum class ShipmentControlAlertType {
    SENT_BUT_NOT_ARRIVED,
    MISSING_ARRIVAL_SCAN,
    ARRIVED_NOT_OUT_FOR_DELIVERY,
    ROUTE_DEVIATION,
    SHIPMENT_STUCK,
    CASE_OVERDUE,
    SIGNATURE_WITHOUT_DELIVERY,
    DELIVERED_WITHOUT_SIGNATURE,
}

enum class ShipmentControlAlertSeverity { INFO, WARNING, CRITICAL }

data class ShipmentControlAlertResponse(
    val key: String,
    val type: ShipmentControlAlertType,
    val severity: ShipmentControlAlertSeverity,
    val shipmentId: UUID?,
    val trackingCode: String?,
    val parcelId: UUID?,
    val parcelCode: String?,
    val hubId: UUID,
    val routeLegId: UUID?,
    val caseId: UUID?,
    val caseNumber: String?,
    val title: String,
    val description: String,
    val detectedAt: Instant,
    val dueAt: Instant?,
    val overdueMinutes: Long?,
    val lastActivityAt: Instant?,
)

data class ShipmentControlTowerSummaryResponse(
    val total: Int,
    val critical: Int,
    val warning: Int,
    val sentButNotArrived: Int,
    val missingArrivalScan: Int,
    val arrivedNotOutForDelivery: Int,
    val routeDeviation: Int,
    val shipmentStuck: Int,
    val caseOverdue: Int,
    val signatureConsistency: Int,
)
