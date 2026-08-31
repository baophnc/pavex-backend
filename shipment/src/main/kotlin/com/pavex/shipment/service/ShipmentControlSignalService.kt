package com.pavex.shipment.service

import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.entity.ShipmentControlSignal
import com.pavex.shipment.entity.ShipmentControlSignalType
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.repository.ShipmentControlSignalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

@Service
class ShipmentControlSignalService(
    private val signals: ShipmentControlSignalRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordRejectedMovement(
        actorUserId: UUID,
        target: ParcelScanTarget,
        request: RecordParcelScanRequest,
        exception: ShipmentApiException,
    ) {
        if (exception.code !in ROUTE_DEVIATION_CODES) return
        val now = clock.instant()
        signals.save(
            ShipmentControlSignal(
                shipmentId = target.shipmentId,
                parcelId = target.parcelId,
                type = ShipmentControlSignalType.ROUTE_DEVIATION,
                code = exception.code,
                hubId = request.hubId,
                routeLegId = request.routeLegId,
                actorUserId = actorUserId,
                description = exception.message.take(500),
                occurredAt = request.occurredAt ?: now,
                createdAt = now,
            ),
        )
    }

    companion object {
        private val ROUTE_DEVIATION_CODES = setOf(
            "scan_hub_mismatch",
            "route_leg_required",
            "assignment_route_leg_mismatch",
            "assignment_hub_mismatch",
            "linehaul_assignment_missing",
        )
    }
}
