package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.WorkforceAssignmentSnapshot
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.dto.RecordParcelScanRequest
import com.pavex.shipment.dto.WorkforceScanAction
import com.pavex.shipment.dto.WorkforceScanRequest
import com.pavex.shipment.dto.WorkforceScanResponse
import com.pavex.shipment.dto.WorkforceScanResult
import com.pavex.shipment.entity.Parcel
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Turns "I scanned this code while doing X" into the precise parcel movement the shipment
 * rules expect (next status, hub, assignment and route leg), so the field app never has to
 * know them. A tracking code moves every parcel of the shipment that can make the move.
 */
@Service
class WorkforceScanService(
    private val views: WorkforceViewService,
    private val shipments: ShipmentRepository,
    private val operations: OperationsShipmentClient,
    private val scans: RecordParcelScanService,
    private val transactions: TransactionTemplate,
) {
    fun scan(actor: PavexActor, request: WorkforceScanRequest): WorkforceScanResponse {
        if (request.action in HUB_ACTIONS && request.hubId == null) {
            throw InvalidShipmentRequestException("hub_required", "Chọn bưu cục đang làm việc trước khi quét.")
        }
        val assignments = operations.workforceAssignments(actor.userId)
        val targets = readTargets(actor, request.code, assignments)
        val shipmentAssignments = assignments.filter { it.shipmentId == targets.shipmentId }

        val results = targets.parcels.map { parcel ->
            val plan = plan(request, targets, parcel, shipmentAssignments)
            if (plan is ScanPlan.Skip) {
                return@map WorkforceScanResult(parcel.code, plan.ok, parcel.status, plan.code, plan.message, plan.assignmentId)
            }
            plan as ScanPlan.Move
            try {
                val updated = scans.record(
                    actor.userId,
                    RecordParcelScanRequest(
                        parcelCode = parcel.code,
                        hubId = plan.hubId,
                        nextStatus = plan.nextStatus,
                        assignmentId = plan.assignmentId,
                        routeLegId = plan.routeLegId,
                    ),
                )
                val status = updated.parcels.firstOrNull { it.code == parcel.code }?.status ?: parcel.status
                WorkforceScanResult(parcel.code, true, status, null, successMessage(request.action, status))
            } catch (exception: ShipmentApiException) {
                WorkforceScanResult(parcel.code, false, parcel.status, exception.code, WorkforceScanMessages.of(exception))
            } catch (_: IllegalStateException) {
                WorkforceScanResult(parcel.code, false, parcel.status, "invalid_parcel_transition", "Kiện không thể chuyển sang trạng thái này.")
            } catch (_: IllegalArgumentException) {
                WorkforceScanResult(parcel.code, false, parcel.status, "invalid_parcel_transition", "Kiện không thể chuyển sang trạng thái này.")
            }
        }

        val shipment = transactions.execute { views.view(shipments.findById(targets.shipmentId).orElseThrow()) }!!
        return WorkforceScanResponse(targets.trackingCode, results, shipment)
    }

    private fun readTargets(actor: PavexActor, code: String, assignments: List<WorkforceAssignmentSnapshot>): ScanTargets =
        transactions.execute {
            val shipment = views.findShipment(code)
            views.requireVisible(actor, shipment, assignments.filter { it.shipmentId == shipment.id })
            if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
                throw InvalidShipmentRequestException("shipment_closed", "Đơn hàng đã kết thúc (đã giao, đã hoàn hoặc đã hủy).")
            }
            val normalized = ShipmentCodeNormalizer.normalizeOrNull(code)
            val parcels = if (normalized != null && normalized.contains('-')) {
                shipment.parcels.filter { it.code == normalized }
            } else {
                shipment.parcels.filter { it.status !in TERMINAL }.sortedBy { it.sequence }
            }
            ScanTargets(shipment.id, shipment.trackingCode, shipment.originHubId, shipment.destinationHubId, parcels.map(ParcelState::of))
        }!!

    private fun plan(
        request: WorkforceScanRequest,
        targets: ScanTargets,
        parcel: ParcelState,
        assignments: List<WorkforceAssignmentSnapshot>,
    ): ScanPlan = when (request.action) {
        WorkforceScanAction.PICK_UP -> when (parcel.status) {
            ParcelStatus.AWAITING_PICKUP -> owned(assignments, OperationsAssignmentType.PICKUP, null) { assignment ->
                ScanPlan.Move(ParcelStatus.PICKED_UP, targets.originHubId, assignment.id, null)
            }
            else -> ScanPlan.Skip(true, "already_done", "Kiện đã được lấy trước đó.")
        }

        WorkforceScanAction.OUT_FOR_DELIVERY -> when (parcel.status) {
            ParcelStatus.AT_DESTINATION_HUB, ParcelStatus.DELIVERY_FAILED ->
                owned(assignments, OperationsAssignmentType.LAST_MILE_DELIVERY, null) { assignment ->
                    ScanPlan.Move(ParcelStatus.OUT_FOR_DELIVERY, targets.destinationHubId, assignment.id, null)
                }
            ParcelStatus.OUT_FOR_DELIVERY -> ScanPlan.Skip(true, "already_done", "Kiện đã ở trong chuyến giao của bạn.")
            else -> ScanPlan.Skip(false, "not_at_delivery_hub", "Kiện chưa về bưu cục giao nên chưa xuất giao được.")
        }

        WorkforceScanAction.DEPART -> {
            val hubId = parcel.currentHubId
            when {
                parcel.status == ParcelStatus.IN_TRANSIT -> ScanPlan.Skip(true, "already_done", "Kiện đã lên xe.")
                hubId == null || parcel.status !in DEPARTABLE ->
                    ScanPlan.Skip(false, "not_departable", "Kiện chưa nhập kho nên chưa lên xe được.")
                else -> owned(assignments, OperationsAssignmentType.LINE_HAUL, hubId) { assignment ->
                    ScanPlan.Move(ParcelStatus.IN_TRANSIT, hubId, assignment.id, assignment.routeLegId)
                }
            }
        }

        WorkforceScanAction.RECEIVE -> receive(request.hubId!!, targets, parcel)

        WorkforceScanAction.CHECK -> when {
            parcel.currentHubId != request.hubId || parcel.status !in WorkforceViewService.ON_SHELF ->
                ScanPlan.Skip(false, "not_in_stock", "Kiện không nằm trong kho của bưu cục này.")
            else -> ScanPlan.Move(null, request.hubId!!, null, null)
        }
    }

    private fun receive(hubId: UUID, targets: ScanTargets, parcel: ParcelState): ScanPlan = when (parcel.status) {
        ParcelStatus.PICKED_UP ->
            if (hubId == targets.originHubId) ScanPlan.Move(ParcelStatus.AT_ORIGIN_HUB, hubId, null, null)
            else ScanPlan.Skip(false, "wrong_hub", "Kiện phải nhập vào bưu cục gửi của đơn.")
        ParcelStatus.IN_TRANSIT -> {
            val leg = parcel.currentRouteLegId
            if (leg == null) {
                ScanPlan.Skip(false, "route_leg_missing", "Không xác định được chuyến xe chở kiện này.")
            } else {
                val next = if (hubId == targets.destinationHubId) ParcelStatus.AT_DESTINATION_HUB else ParcelStatus.AT_TRANSIT_HUB
                ScanPlan.Move(next, hubId, null, leg)
            }
        }
        ParcelStatus.RETURNING ->
            if (hubId == targets.originHubId) ScanPlan.Move(ParcelStatus.RETURNED, hubId, null, null)
            else ScanPlan.Skip(false, "wrong_hub", "Hàng hoàn phải nhập về bưu cục gửi.")
        in WorkforceViewService.ON_SHELF ->
            if (parcel.currentHubId == hubId) ScanPlan.Skip(true, "already_done", "Kiện đã có trong kho.")
            else ScanPlan.Skip(false, "not_receivable", "Kiện đang ở kho khác, chưa được chuyển tới đây.")
        else -> ScanPlan.Skip(false, "not_receivable", "Kiện đang ở trạng thái không thể nhập kho.")
    }

    private inline fun owned(
        assignments: List<WorkforceAssignmentSnapshot>,
        type: OperationsAssignmentType,
        hubId: UUID?,
        move: (WorkforceAssignmentSnapshot) -> ScanPlan,
    ): ScanPlan {
        val assignment = assignments.firstOrNull { it.type == type && (hubId == null || it.hubId == hubId) }
            ?: return ScanPlan.Skip(false, "no_assignment", "Bạn chưa được giao việc này cho đơn hàng.")
        return when (assignment.status) {
            OperationsAssignmentStatus.IN_PROGRESS -> move(assignment)
            OperationsAssignmentStatus.ASSIGNED, OperationsAssignmentStatus.ACCEPTED -> ScanPlan.Skip(
                false,
                "assignment_not_started",
                "Bấm “Bắt đầu” việc này trước khi quét.",
                assignment.id,
            )
            else -> ScanPlan.Skip(false, "no_assignment", "Việc này không còn mở.")
        }
    }

    private fun successMessage(action: WorkforceScanAction, status: ParcelStatus): String = when (action) {
        WorkforceScanAction.CHECK -> "Đã xác nhận kiện có trong kho."
        else -> "Đã chuyển sang: ${ShipmentVietnameseText.parcelStatus(status)}."
    }

    private data class ScanTargets(
        val shipmentId: UUID,
        val trackingCode: String,
        val originHubId: UUID,
        val destinationHubId: UUID,
        val parcels: List<ParcelState>,
    )

    private data class ParcelState(
        val code: String,
        val status: ParcelStatus,
        val currentHubId: UUID?,
        val currentRouteLegId: UUID?,
    ) {
        companion object {
            fun of(parcel: Parcel) = ParcelState(parcel.code, parcel.status, parcel.currentHubId, parcel.currentRouteLegId)
        }
    }

    private sealed interface ScanPlan {
        data class Move(val nextStatus: ParcelStatus?, val hubId: UUID, val assignmentId: UUID?, val routeLegId: UUID?) : ScanPlan
        data class Skip(val ok: Boolean, val code: String, val message: String, val assignmentId: UUID? = null) : ScanPlan
    }

    companion object {
        private val HUB_ACTIONS = setOf(WorkforceScanAction.RECEIVE, WorkforceScanAction.CHECK)
        private val TERMINAL = setOf(ParcelStatus.DELIVERED, ParcelStatus.RETURNED, ParcelStatus.CANCELLED)
        private val DEPARTABLE = setOf(ParcelStatus.AT_ORIGIN_HUB, ParcelStatus.AT_TRANSIT_HUB)
    }
}

/** Plain-language reasons for the rejections a field worker can hit while scanning. */
object WorkforceScanMessages {
    private val messages = mapOf(
        "scan_hub_forbidden" to "Bạn không thuộc bưu cục này nên không quét được.",
        "hub_handling_assignment_missing" to "Kho chưa nhận việc xử lý đơn này. Trưởng bưu cục cần phân công trước.",
        "assignment_not_ready" to "Việc xử lý đơn này chưa được nhận. Hãy nhận việc rồi quét lại.",
        "assignment_not_in_progress" to "Bấm “Bắt đầu” việc này trước khi quét.",
        "assignment_not_found" to "Bạn không được giao việc này.",
        "linehaul_assignment_missing" to "Chuyến xe chở kiện này chưa có tài xế nhận.",
        "scan_hub_mismatch" to "Bưu cục này không nằm trên hành trình của đơn.",
        "shipment_lifecycle_closed" to "Đơn hàng đã kết thúc.",
        "invalid_parcel_transition" to "Kiện không thể chuyển sang trạng thái này.",
        "operations_unavailable" to "Hệ thống điều phối đang bận, vui lòng quét lại.",
    )

    fun of(exception: ShipmentApiException): String = messages[exception.code] ?: exception.message
}
