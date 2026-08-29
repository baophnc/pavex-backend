package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "parcels",
    schema = "shipments",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_shipment_parcel_code", columnNames = ["code"]),
        UniqueConstraint(name = "ux_shipment_parcel_sequence", columnNames = ["shipment_id", "sequence"]),
    ],
)
class Parcel(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    var shipment: Shipment = Shipment(),
    @Column(nullable = false, length = 40)
    var code: String = "",
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(nullable = false, length = 500)
    var description: String = "",
    @Column(nullable = false)
    var quantity: Int = 1,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var category: GoodsCategory = GoodsCategory.OTHER,
    @Column(name = "is_fragile", nullable = false)
    var isFragile: Boolean = false,
    @Column(name = "actual_weight_kg", nullable = false, precision = 12, scale = 3)
    var actualWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "volumetric_weight_kg", nullable = false, precision = 12, scale = 3)
    var volumetricWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "chargeable_weight_kg", nullable = false, precision = 12, scale = 3)
    var chargeableWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "length_cm", nullable = false, precision = 12, scale = 2)
    var lengthCm: BigDecimal = BigDecimal.ZERO,
    @Column(name = "width_cm", nullable = false, precision = 12, scale = 2)
    var widthCm: BigDecimal = BigDecimal.ZERO,
    @Column(name = "height_cm", nullable = false, precision = 12, scale = 2)
    var heightCm: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ParcelStatus = ParcelStatus.AWAITING_PICKUP,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var stage: ParcelStage = ParcelStage.AWAITING_PICKUP,
    @Enumerated(EnumType.STRING)
    @Column(name = "flow_direction", nullable = false, length = 20)
    var flowDirection: ParcelFlowDirection = ParcelFlowDirection.FORWARD,
    @Enumerated(EnumType.STRING)
    @Column(name = "current_location_type", nullable = false, length = 20)
    var currentLocationType: ParcelLocationType = ParcelLocationType.SENDER,
    @Column(name = "current_hub_id")
    var currentHubId: UUID? = null,
    @Column(name = "current_hub_name", length = 200)
    var currentHubName: String? = null,
    @Column(name = "current_route_leg_id")
    var currentRouteLegId: UUID? = null,
    @Column(name = "current_assignment_id")
    var currentAssignmentId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "custody_type", nullable = false, length = 20)
    var custodyType: ParcelCustodyType = ParcelCustodyType.SENDER,
    @Column(name = "custody_reference_id")
    var custodyReferenceId: UUID? = null,
    @Version
    var version: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun canTransitionTo(nextStatus: ParcelStatus): Boolean =
        ALLOWED_TRANSITIONS[status].orEmpty().contains(nextStatus)

    fun transitionTo(
        nextStatus: ParcelStatus,
        occurredAt: Instant,
        actorUserId: UUID? = null,
        hubId: UUID? = null,
        hubName: String? = null,
    ) {
        check(canTransitionTo(nextStatus)) {
            "Parcel cannot transition from $status to $nextStatus."
        }

        status = nextStatus
        when (nextStatus) {
            ParcelStatus.AWAITING_PICKUP -> {
                stage = ParcelStage.AWAITING_PICKUP
                currentLocationType = ParcelLocationType.SENDER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.SENDER
                custodyReferenceId = null
            }
            ParcelStatus.PICKED_UP -> {
                stage = ParcelStage.PICKED_UP
                currentLocationType = ParcelLocationType.COURIER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.COURIER
                custodyReferenceId = actorUserId
            }
            ParcelStatus.AT_ORIGIN_HUB, ParcelStatus.AT_DESTINATION_HUB -> {
                val requiredHubId = requireNotNull(hubId) { "$nextStatus requires a hub." }
                stage = ParcelStage.AT_HUB
                currentLocationType = ParcelLocationType.HUB
                currentHubId = requiredHubId
                currentHubName = hubName
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.HUB
                custodyReferenceId = requiredHubId
            }
            ParcelStatus.IN_TRANSIT -> {
                stage = ParcelStage.IN_TRANSIT
                currentLocationType = ParcelLocationType.UNKNOWN
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.LINE_HAUL
                custodyReferenceId = actorUserId
            }
            ParcelStatus.OUT_FOR_DELIVERY -> {
                stage = ParcelStage.OUT_FOR_DELIVERY
                currentLocationType = ParcelLocationType.COURIER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.COURIER
                custodyReferenceId = actorUserId
            }
            ParcelStatus.DELIVERY_FAILED -> {
                stage = ParcelStage.DELIVERY_EXCEPTION
                currentLocationType = ParcelLocationType.COURIER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                custodyType = ParcelCustodyType.COURIER
                custodyReferenceId = actorUserId ?: custodyReferenceId
            }
            ParcelStatus.DELIVERED -> {
                stage = ParcelStage.DELIVERED
                currentLocationType = ParcelLocationType.RECIPIENT
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.RECIPIENT
                custodyReferenceId = null
            }
            ParcelStatus.RETURNING -> {
                stage = ParcelStage.RETURNING
                flowDirection = ParcelFlowDirection.RETURN
                currentLocationType = ParcelLocationType.UNKNOWN
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.LINE_HAUL
                custodyReferenceId = actorUserId
            }
            ParcelStatus.RETURNED -> {
                stage = ParcelStage.RETURNED
                flowDirection = ParcelFlowDirection.RETURN
                currentLocationType = ParcelLocationType.SENDER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.SENDER
                custodyReferenceId = null
            }
            ParcelStatus.CANCELLED -> {
                stage = ParcelStage.CANCELLED
                currentLocationType = ParcelLocationType.SENDER
                currentHubId = null
                currentHubName = null
                currentRouteLegId = null
                currentAssignmentId = null
                custodyType = ParcelCustodyType.SENDER
                custodyReferenceId = null
            }
        }
        updatedAt = occurredAt
    }

    fun cancel(cancelledAt: Instant) {
        require(status == ParcelStatus.AWAITING_PICKUP) { "Parcel can only be cancelled before pickup." }
        status = ParcelStatus.CANCELLED
        stage = ParcelStage.CANCELLED
        updatedAt = cancelledAt
    }

    companion object {
        private val ALLOWED_TRANSITIONS: Map<ParcelStatus, Set<ParcelStatus>> = mapOf(
            ParcelStatus.AWAITING_PICKUP to setOf(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED),
            ParcelStatus.PICKED_UP to setOf(ParcelStatus.AT_ORIGIN_HUB),
            ParcelStatus.AT_ORIGIN_HUB to setOf(ParcelStatus.IN_TRANSIT),
            ParcelStatus.IN_TRANSIT to setOf(ParcelStatus.AT_DESTINATION_HUB, ParcelStatus.RETURNING),
            ParcelStatus.AT_DESTINATION_HUB to setOf(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.RETURNING),
            ParcelStatus.OUT_FOR_DELIVERY to setOf(ParcelStatus.DELIVERED, ParcelStatus.DELIVERY_FAILED),
            ParcelStatus.DELIVERY_FAILED to setOf(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.RETURNING),
            ParcelStatus.RETURNING to setOf(ParcelStatus.RETURNED),
        )

        fun create(
            shipment: Shipment,
            code: String,
            sequence: Int,
            draft: ParcelDraft,
            createdAt: Instant,
        ): Parcel {
            val normalizedCode = code.trim().uppercase()
            require(normalizedCode.isNotEmpty() && normalizedCode.length <= 40) { "Parcel code is invalid." }
            require(sequence in 1..99) { "Parcel sequence must be between 1 and 99." }
            val description = draft.description.trim()
            require(description.isNotEmpty() && description.length <= 500) {
                "Parcel description is required and must not exceed 500 characters."
            }
            require(draft.quantity in 1..10_000) { "Parcel quantity is invalid." }
            requireMetric(draft.actualWeightKg, "actualWeightKg")
            requireMetric(draft.volumetricWeightKg, "volumetricWeightKg")
            requireMetric(draft.chargeableWeightKg, "chargeableWeightKg")
            requireMetric(draft.lengthCm, "lengthCm")
            requireMetric(draft.widthCm, "widthCm")
            requireMetric(draft.heightCm, "heightCm")
            require(draft.chargeableWeightKg >= draft.actualWeightKg && draft.chargeableWeightKg >= draft.volumetricWeightKg) {
                "Chargeable weight cannot be lower than actual or volumetric weight."
            }

            return Parcel(
                shipment = shipment,
                code = normalizedCode,
                sequence = sequence,
                description = description,
                quantity = draft.quantity,
                category = draft.category,
                isFragile = draft.isFragile || draft.category == GoodsCategory.FRAGILE_GOODS,
                actualWeightKg = draft.actualWeightKg,
                volumetricWeightKg = draft.volumetricWeightKg,
                chargeableWeightKg = draft.chargeableWeightKg,
                lengthCm = draft.lengthCm,
                widthCm = draft.widthCm,
                heightCm = draft.heightCm,
                createdAt = createdAt,
                updatedAt = createdAt,
            )
        }

        private fun requireMetric(value: BigDecimal, field: String) {
            require(value > BigDecimal.ZERO) { "$field must be greater than zero." }
        }
    }
}
