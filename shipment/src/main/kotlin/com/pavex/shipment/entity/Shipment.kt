package com.pavex.shipment.entity

import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "shipments",
    schema = "shipments",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_shipment_tracking_code", columnNames = ["tracking_code"]),
        UniqueConstraint(name = "ux_shipment_quote_id", columnNames = ["quote_id"]),
        UniqueConstraint(
            name = "ux_shipment_creator_idempotency",
            columnNames = ["created_by_user_id", "idempotency_key"],
        ),
    ],
)
class Shipment(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "tracking_code", nullable = false, length = 32)
    var trackingCode: String = "",
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(name = "created_by_user_id", nullable = false)
    var createdByUserId: UUID = UUID.randomUUID(),
    @Column(name = "idempotency_key", nullable = false, length = 100)
    var idempotencyKey: String = "",
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    var requestFingerprint: String = "",
    @Column(name = "merchant_reference", length = 100)
    var merchantReference: String? = null,
    /** The merchant pickup address the sender was taken from; null for shipments created before it was recorded. */
    @Column(name = "pickup_address_id")
    var pickupAddressId: UUID? = null,
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "contactName", column = Column(name = "sender_contact_name", nullable = false, length = 120)),
        AttributeOverride(name = "contactPhone", column = Column(name = "sender_contact_phone", nullable = false, length = 20)),
        AttributeOverride(name = "addressLine", column = Column(name = "sender_address_line", nullable = false, length = 500)),
        AttributeOverride(name = "provinceCode", column = Column(name = "sender_province_code", nullable = false, length = 20)),
        AttributeOverride(name = "provinceName", column = Column(name = "sender_province_name", nullable = false, length = 160)),
        AttributeOverride(name = "wardCode", column = Column(name = "sender_ward_code", nullable = false, length = 20)),
        AttributeOverride(name = "wardName", column = Column(name = "sender_ward_name", nullable = false, length = 160)),
    )
    var sender: ShipmentAddressSnapshot = ShipmentAddressSnapshot(),
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "contactName", column = Column(name = "recipient_contact_name", nullable = false, length = 120)),
        AttributeOverride(name = "contactPhone", column = Column(name = "recipient_contact_phone", nullable = false, length = 20)),
        AttributeOverride(name = "addressLine", column = Column(name = "recipient_address_line", nullable = false, length = 500)),
        AttributeOverride(name = "provinceCode", column = Column(name = "recipient_province_code", nullable = false, length = 20)),
        AttributeOverride(name = "provinceName", column = Column(name = "recipient_province_name", nullable = false, length = 160)),
        AttributeOverride(name = "wardCode", column = Column(name = "recipient_ward_code", nullable = false, length = 20)),
        AttributeOverride(name = "wardName", column = Column(name = "recipient_ward_name", nullable = false, length = 160)),
    )
    var recipient: ShipmentAddressSnapshot = ShipmentAddressSnapshot(),
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_fee_payer", nullable = false, length = 20)
    var shippingFeePayer: ShippingFeePayer = ShippingFeePayer.SENDER,
    @Column(name = "quote_id", nullable = false)
    var quoteId: UUID = UUID.randomUUID(),
    @Column(name = "quote_request_id", nullable = false)
    var quoteRequestId: UUID = UUID.randomUUID(),
    @Column(name = "rate_plan_id", nullable = false)
    var ratePlanId: UUID = UUID.randomUUID(),
    @Column(name = "rate_code", nullable = false, length = 60)
    var rateCode: String = "",
    @Column(name = "rate_revision", nullable = false)
    var rateRevision: Int = 1,
    @Column(name = "rate_version", nullable = false, length = 100)
    var rateVersion: String = "",
    @Column(nullable = false, length = 3)
    var currency: String = "VND",
    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false, length = 20)
    var serviceLevel: ShipmentServiceLevel = ShipmentServiceLevel.STANDARD,
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_zone", nullable = false, length = 30)
    var pricingZone: ShipmentPricingZone = ShipmentPricingZone.SAME_PROVINCE,
    @Column(name = "base_fee", nullable = false)
    var baseFee: Long = 0,
    @Column(name = "additional_weight_fee", nullable = false)
    var additionalWeightFee: Long = 0,
    @Column(name = "area_surcharge", nullable = false)
    var areaSurcharge: Long = 0,
    @Column(name = "cod_fee", nullable = false)
    var codFee: Long = 0,
    @Column(name = "insurance_fee", nullable = false)
    var insuranceFee: Long = 0,
    @Column(name = "shipping_fee", nullable = false)
    var shippingFee: Long = 0,
    @Column(name = "cod_amount", nullable = false)
    var codAmount: Long = 0,
    @Column(name = "declared_value", nullable = false)
    var declaredValue: Long = 0,
    @Column(name = "recipient_collect_amount", nullable = false)
    var recipientCollectAmount: Long = 0,
    @Column(name = "collected_amount")
    var collectedAmount: Long? = null,
    @Column(name = "collected_at")
    var collectedAt: Instant? = null,
    @Column(name = "collected_by_user_id")
    var collectedByUserId: UUID? = null,
    @Column(name = "collection_attempt_id")
    var collectionAttemptId: UUID? = null,
    @Column(name = "estimated_min_days", nullable = false)
    var estimatedMinDays: Int = 0,
    @Column(name = "estimated_max_days", nullable = false)
    var estimatedMaxDays: Int = 0,
    @Column(name = "quote_expires_at", nullable = false)
    var quoteExpiresAt: Instant = Instant.EPOCH,
    @Column(name = "origin_service_area_id", nullable = false)
    var originServiceAreaId: UUID = UUID.randomUUID(),
    @Column(name = "origin_region_id", nullable = false)
    var originRegionId: UUID = UUID.randomUUID(),
    @Column(name = "origin_hub_id", nullable = false)
    var originHubId: UUID = UUID.randomUUID(),
    @Column(name = "origin_hub_code", nullable = false, length = 60)
    var originHubCode: String = "",
    @Column(name = "origin_hub_name", nullable = false, length = 200)
    var originHubName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_coverage_tier", nullable = false, length = 20)
    var originCoverageTier: ShipmentCoverageTier = ShipmentCoverageTier.STANDARD,
    @Column(name = "destination_service_area_id", nullable = false)
    var destinationServiceAreaId: UUID = UUID.randomUUID(),
    @Column(name = "destination_region_id", nullable = false)
    var destinationRegionId: UUID = UUID.randomUUID(),
    @Column(name = "destination_hub_id", nullable = false)
    var destinationHubId: UUID = UUID.randomUUID(),
    @Column(name = "destination_hub_code", nullable = false, length = 60)
    var destinationHubCode: String = "",
    @Column(name = "destination_hub_name", nullable = false, length = 200)
    var destinationHubName: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "destination_coverage_tier", nullable = false, length = 20)
    var destinationCoverageTier: ShipmentCoverageTier = ShipmentCoverageTier.STANDARD,
    @Enumerated(EnumType.STRING)
    @Column(name = "routing_status", nullable = false, length = 20)
    var routingStatus: ShipmentRoutingStatus = ShipmentRoutingStatus.UNPLANNED,
    @Column(name = "route_revision", nullable = false)
    var routeRevision: Int = 0,
    @Column(name = "capacity_reservation_batch_id")
    var capacityReservationBatchId: UUID? = null,
    @Column(name = "reserved_weight_kg", nullable = false, precision = 12, scale = 3)
    var reservedWeightKg: java.math.BigDecimal = java.math.BigDecimal.ZERO,
    @Column(name = "route_ready_at")
    var routeReadyAt: Instant? = null,
    @Column(name = "estimated_delivery_at")
    var estimatedDeliveryAt: Instant? = null,
    @Column(name = "estimated_transit_minutes")
    var estimatedTransitMinutes: Int? = null,
    @Column(name = "route_distance_km", precision = 12, scale = 3)
    var routeDistanceKm: java.math.BigDecimal? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ShipmentStatus = ShipmentStatus.AWAITING_PICKUP,
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    var lifecycleStatus: ShipmentLifecycleStatus = ShipmentLifecycleStatus.ACTIVE,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(length = 1000)
    var note: String? = null,
    @Column(name = "cancellation_reason", length = 500)
    var cancellationReason: String? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    /** Set when a cancellation completes after Pavex already picked the parcels up. */
    @Column(name = "cancelled_after_pickup", nullable = false)
    var cancelledAfterPickup: Boolean = false,
    @Column(name = "is_on_hold", nullable = false)
    var isOnHold: Boolean = false,
    @Column(name = "hold_reason", length = 1000)
    var holdReason: String? = null,
    @Column(name = "hold_actor_user_id")
    var holdActorUserId: UUID? = null,
    @Column(name = "hold_hub_id")
    var holdHubId: UUID? = null,
    @Column(name = "held_at")
    var heldAt: Instant? = null,
    @Column(name = "has_pending_weight_review", nullable = false)
    var hasPendingWeightReview: Boolean = false,
    @Column(name = "network_confirmed_at")
    var networkConfirmedAt: Instant? = null,
    @Column(name = "network_confirmation_error", length = 200)
    var networkConfirmationError: String? = null,
    @Version
    var version: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @OneToMany(mappedBy = "shipment", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    val parcels: MutableList<Parcel> = mutableListOf()

    @OneToMany(mappedBy = "shipment", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("occurredAt ASC")
    val events: MutableList<ShipmentEvent> = mutableListOf()

    /** The part of [recipientCollectAmount] that is the shipping fee, when the recipient pays it. */
    fun recipientShippingFee(): Long = if (shippingFeePayer == ShippingFeePayer.RECIPIENT) shippingFee else 0

    /**
     * The shipping fee the merchant is charged: always when the sender pays, and also when a
     * recipient-paid shipment comes back returned or is cancelled after pickup (the recipient
     * never paid). Only a cancellation before pickup is free.
     */
    fun merchantShippingFee(): Long = when {
        status == ShipmentStatus.CANCELLED -> if (cancelledAfterPickup) shippingFee else 0
        status == ShipmentStatus.RETURNED -> shippingFee
        shippingFeePayer == ShippingFeePayer.SENDER -> shippingFee
        else -> 0
    }

    fun collectionStatus(): ShipmentCollectionStatus = when {
        collectedAmount != null -> ShipmentCollectionStatus.COLLECTED
        recipientCollectAmount == 0L -> ShipmentCollectionStatus.NOT_REQUIRED
        lifecycleStatus != ShipmentLifecycleStatus.ACTIVE -> ShipmentCollectionStatus.VOIDED
        else -> ShipmentCollectionStatus.PENDING
    }

    /**
     * Records the money the courier collected from the recipient. It happens once per shipment,
     * for the full amount, on the first parcel delivered successfully.
     */
    fun recordCollection(
        amount: Long,
        courierUserId: UUID,
        attemptId: UUID,
        parcelId: UUID,
        hubId: UUID,
        locationName: String,
        occurredAt: Instant,
        recordedAt: Instant,
    ) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Shipment lifecycle is already closed." }
        check(collectionStatus() == ShipmentCollectionStatus.PENDING) { "This shipment has nothing left to collect." }
        require(amount == recipientCollectAmount) { "The courier must collect exactly $recipientCollectAmount VND." }
        require(!recordedAt.isBefore(occurredAt)) { "Recorded time cannot precede occurrence time." }
        collectedAmount = amount
        collectedAt = occurredAt
        collectedByUserId = courierUserId
        collectionAttemptId = attemptId
        updatedAt = recordedAt
        events += ShipmentEvent(
            shipment = this,
            parcelId = parcelId,
            status = status,
            code = "shipment.cash-collected",
            description = "Bưu tá đã thu tiền của người nhận.",
            actorUserId = courierUserId,
            hubId = hubId,
            locationName = locationName.trim().takeIf { it.isNotEmpty() }?.take(200),
            occurredAt = occurredAt,
            recordedAt = recordedAt,
        )
    }

    fun assignReservedRoute(
        routeRevision: Int,
        capacityReservationBatchId: UUID?,
        reservedWeightKg: java.math.BigDecimal,
        readyAt: Instant,
        estimatedDeliveryAt: Instant,
        estimatedTransitMinutes: Int,
        distanceKm: java.math.BigDecimal?,
        updatedAt: Instant,
    ) {
        require(routeRevision > 0) { "routeRevision must be positive." }
        require(reservedWeightKg > java.math.BigDecimal.ZERO) { "reservedWeightKg must be positive." }
        require(!estimatedDeliveryAt.isBefore(readyAt)) { "estimatedDeliveryAt cannot be before readyAt." }
        require(estimatedTransitMinutes >= 0) { "estimatedTransitMinutes must not be negative." }
        require(distanceKm == null || distanceKm >= java.math.BigDecimal.ZERO) { "distanceKm must not be negative." }
        require(routingStatus == ShipmentRoutingStatus.UNPLANNED) { "Shipment route is already assigned." }

        this.routeRevision = routeRevision
        this.capacityReservationBatchId = capacityReservationBatchId
        this.reservedWeightKg = reservedWeightKg
        this.routeReadyAt = readyAt
        this.estimatedDeliveryAt = estimatedDeliveryAt
        this.estimatedTransitMinutes = estimatedTransitMinutes
        this.routeDistanceKm = distanceKm
        this.routingStatus = ShipmentRoutingStatus.RESERVED
        this.updatedAt = updatedAt
    }

    fun cancel(reason: String, actorUserId: UUID, cancelledAt: Instant) {
        require(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Shipment lifecycle is already closed." }
        require(parcels.all { it.status == ParcelStatus.AWAITING_PICKUP }) {
            "A shipment can only be cancelled before pickup."
        }
        val normalizedReason = reason.trim()
        require(normalizedReason.isNotEmpty() && normalizedReason.length <= 500) {
            "Cancellation reason is required and must not exceed 500 characters."
        }

        parcels.forEach { it.cancel(cancelledAt) }
        status = ShipmentStatus.CANCELLED
        lifecycleStatus = ShipmentLifecycleStatus.CANCELLED
        cancellationReason = normalizedReason
        this.cancelledAt = cancelledAt
        updatedAt = cancelledAt
        events += ShipmentEvent(
            shipment = this,
            status = ShipmentStatus.CANCELLED,
            code = "shipment.cancelled",
            description = normalizedReason,
            actorUserId = actorUserId,
            locationName = sender.wardName,
            occurredAt = cancelledAt,
            recordedAt = cancelledAt,
        )
    }

    /**
     * Applies [change] and records it on the timeline. Returns the Vietnamese names of the
     * fields that changed; nothing is recorded when the change is a no-op.
     */
    fun updateDetails(change: ShipmentDetailsChange, actorUserId: UUID, reason: String?, now: Instant): List<String> {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Only active shipments can be edited." }
        val changed = mutableListOf<String>()
        var nextRecipient = recipient
        change.recipientName?.let { requireEventText(it, 120, "recipientName") }?.takeIf { it != recipient.contactName }?.let {
            nextRecipient = nextRecipient.copy(contactName = it)
            changed += "tên người nhận"
        }
        change.recipientPhone?.let {
            requireNotNull(com.pavex.common.contact.VietnamesePhone.normalize(it)) { "recipientPhone must be a valid Vietnamese phone number." }
        }?.takeIf { it != recipient.contactPhone }?.let {
            nextRecipient = nextRecipient.copy(contactPhone = it)
            changed += "số điện thoại người nhận"
        }
        change.recipientAddressLine?.let { requireEventText(it, 500, "recipientAddressLine") }?.takeIf { it != recipient.addressLine }?.let {
            nextRecipient = nextRecipient.copy(addressLine = it)
            changed += "địa chỉ người nhận"
        }
        if (nextRecipient !== recipient) recipient = nextRecipient
        change.note?.let { optionalText(it, 1000, "note") }.let { next ->
            if (change.note != null && next != note) {
                note = next
                changed += "ghi chú"
            }
        }
        change.merchantReference?.let { optionalText(it, 100, "merchantReference") }.let { next ->
            if (change.merchantReference != null && next != merchantReference) {
                merchantReference = next
                changed += "mã đơn của shop"
            }
        }
        val nextSender = change.sender
        if (nextSender != null && change.pickupAddressId != null && change.pickupAddressId != pickupAddressId) {
            require(nextSender.provinceCode == sender.provinceCode && nextSender.wardCode == sender.wardCode) {
                "A new pickup point must be in the same ward; otherwise cancel and create the shipment again."
            }
            sender = nextSender
            pickupAddressId = change.pickupAddressId
            changed += "điểm lấy hàng"
        }
        if (changed.isEmpty()) return changed

        val summary = "Cập nhật ${changed.joinToString(", ")}"
        val withReason = reason?.trim()?.takeIf(String::isNotEmpty)?.let { "$summary. Lý do: $it" } ?: summary
        events += ShipmentEvent(
            shipment = this,
            status = status,
            code = "shipment.details-updated",
            description = withReason.take(500),
            actorUserId = actorUserId,
            occurredAt = now,
            recordedAt = now,
        )
        updatedAt = now
        return changed
    }

    private fun optionalText(value: String, maxLength: Int, field: String): String? =
        value.trim().takeIf(String::isNotEmpty)?.also {
            require(it.length <= maxLength) { "$field must not exceed $maxLength characters." }
        }

    fun markRouteReleased(releasedAt: Instant) {
        if (routingStatus == ShipmentRoutingStatus.RELEASED) return
        routingStatus = ShipmentRoutingStatus.RELEASED
        updatedAt = releasedAt
    }

    fun placeHold(reason: String, actorUserId: UUID, hubId: UUID?, now: Instant) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Only active shipments can be placed on hold." }
        check(!isOnHold) { "Shipment is already on hold." }
        val normalized = requireEventText(reason, 1000, "reason")
        isOnHold = true
        holdReason = normalized
        holdActorUserId = actorUserId
        holdHubId = hubId
        heldAt = now
        updatedAt = now
        recordAdministrativeEvent("shipment.hold.placed", normalized, actorUserId, hubId, now)
    }

    fun releaseHold(reason: String, actorUserId: UUID, hubId: UUID?, now: Instant) {
        check(isOnHold) { "Shipment is not on hold." }
        check(!hasPendingWeightReview) { "Weight review must be completed before the shipment hold can be released." }
        val normalized = requireEventText(reason, 1000, "reason")
        isOnHold = false
        holdReason = null
        holdActorUserId = null
        holdHubId = null
        heldAt = null
        updatedAt = now
        recordAdministrativeEvent("shipment.hold.released", normalized, actorUserId, hubId, now)
    }

    fun recordAdministrativeEvent(
        code: String,
        description: String,
        actorUserId: UUID,
        hubId: UUID?,
        now: Instant,
        dataJson: String? = null,
    ) {
        events += ShipmentEvent(
            shipment = this,
            status = status,
            code = requireEventText(code, 100, "code"),
            description = requireEventText(description, 500, "description"),
            actorUserId = actorUserId,
            hubId = hubId,
            locationName = when (hubId) {
                originHubId -> originHubName
                destinationHubId -> destinationHubName
                else -> null
            },
            dataJson = dataJson,
            occurredAt = now,
            recordedAt = now,
        )
        updatedAt = now
    }

    fun adjustParcelWeight(parcelId: UUID, actualWeightKg: java.math.BigDecimal, now: Instant): Pair<java.math.BigDecimal, java.math.BigDecimal> {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Only active shipments can be reweighed." }
        check(!hasPendingWeightReview) { "Complete the pending weight review first." }
        require(actualWeightKg > java.math.BigDecimal.ZERO) { "actualWeightKg must be positive." }
        val parcel = parcels.singleOrNull { it.id == parcelId } ?: error("Parcel does not belong to this shipment.")
        val oldActual = parcel.actualWeightKg
        val oldChargeable = parcel.chargeableWeightKg
        parcel.actualWeightKg = actualWeightKg
        parcel.chargeableWeightKg = maxOf(actualWeightKg, parcel.volumetricWeightKg)
        parcel.updatedAt = now
        hasPendingWeightReview = true
        updatedAt = now
        return oldActual to oldChargeable
    }

    fun completeWeightReview(now: Instant) {
        check(hasPendingWeightReview) { "Shipment has no pending weight review." }
        hasPendingWeightReview = false
        updatedAt = now
    }

    fun prepareReturn(reason: String, actorUserId: UUID, now: Instant, dataJson: String? = null) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Only active shipments can enter the return flow." }
        val returnable = parcels.filter { it.stage !in setOf(ParcelStage.DELIVERED, ParcelStage.RETURNED, ParcelStage.CANCELLED) }
        check(returnable.isNotEmpty()) { "Shipment has no parcels eligible for return." }
        returnable.forEach { it.prepareReturn(now) }
        recordAdministrativeEvent("shipment.return.approved", requireEventText(reason, 500, "reason"), actorUserId, null, now, dataJson)
    }

    fun cancelRecovered(reason: String, actorUserId: UUID, now: Instant, dataJson: String? = null) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Shipment lifecycle is already closed." }
        check(parcels.all { it.stage in setOf(ParcelStage.AWAITING_PICKUP, ParcelStage.AT_HUB, ParcelStage.RETURNED) }) {
            "Cancellation cannot complete until every parcel is recovered at a hub, awaiting pickup, or already returned."
        }
        val normalized = requireEventText(reason, 500, "reason")
        cancelledAfterPickup = parcels.any { it.stage != ParcelStage.AWAITING_PICKUP }
        parcels.forEach { it.cancelRecovered(now) }
        status = ShipmentStatus.CANCELLED
        lifecycleStatus = ShipmentLifecycleStatus.CANCELLED
        cancellationReason = normalized
        cancelledAt = now
        isOnHold = false
        holdReason = null
        holdActorUserId = null
        holdHubId = null
        heldAt = null
        updatedAt = now
        recordAdministrativeEvent("shipment.cancellation.completed", normalized, actorUserId, null, now, dataJson)
    }

    fun transitionParcel(
        parcelId: UUID,
        nextStatus: ParcelStatus,
        eventCode: String,
        description: String,
        actorUserId: UUID,
        hubId: UUID,
        locationName: String,
        occurredAt: Instant,
        recordedAt: Instant,
        routeLegId: UUID? = null,
        assignmentId: UUID? = null,
    ) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Shipment lifecycle is already closed." }
        require(!recordedAt.isBefore(occurredAt)) { "Recorded time cannot precede occurrence time." }
        val parcel = parcels.singleOrNull { it.id == parcelId }
            ?: error("Parcel does not belong to this shipment.")

        parcel.transitionTo(
            nextStatus = nextStatus,
            occurredAt = occurredAt,
            actorUserId = actorUserId,
            hubId = hubId,
            hubName = locationName,
            routeLegId = routeLegId,
            assignmentId = assignmentId,
        )
        status = resolveStatus(parcels)
        updatedAt = recordedAt
        events += ShipmentEvent(
            shipment = this,
            parcelId = parcel.id,
            status = status,
            code = requireEventText(eventCode, 100, "eventCode"),
            description = requireEventText(description, 500, "description"),
            actorUserId = actorUserId,
            hubId = hubId,
            locationName = locationName.trim().takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 200) { "locationName must not exceed 200 characters." }
            },
            occurredAt = occurredAt,
            recordedAt = recordedAt,
        )
        completeIfTerminal(actorUserId, recordedAt)
    }

    /**
     * Closes the shipment once every parcel is delivered or back with the sender, including a mix
     * of both (PARTIALLY_DELIVERED); only a closed shipment is settled with the merchant.
     */
    private fun completeIfTerminal(actorUserId: UUID, completedAt: Instant) {
        if (!parcels.all { it.status == ParcelStatus.DELIVERED || it.status == ParcelStatus.RETURNED }) return
        if (lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) return

        lifecycleStatus = ShipmentLifecycleStatus.COMPLETED
        this.completedAt = completedAt
        if (routingStatus != ShipmentRoutingStatus.RELEASED) {
            routingStatus = ShipmentRoutingStatus.CONSUMED
        }
        updatedAt = completedAt
        events += ShipmentEvent(
            shipment = this,
            status = status,
            code = "shipment.completed",
            description = "Đã hoàn tất vòng đời đơn hàng.",
            actorUserId = actorUserId,
            occurredAt = completedAt,
            recordedAt = completedAt,
        )
    }

    fun recordParcelCheckpoint(
        parcelId: UUID,
        description: String,
        actorUserId: UUID,
        hubId: UUID,
        locationName: String,
        occurredAt: Instant,
        recordedAt: Instant,
        routeLegId: UUID? = null,
        assignmentId: UUID? = null,
    ) {
        check(lifecycleStatus == ShipmentLifecycleStatus.ACTIVE) { "Shipment lifecycle is already closed." }
        require(!recordedAt.isBefore(occurredAt)) { "Recorded time cannot precede occurrence time." }
        val parcel = parcels.singleOrNull { it.id == parcelId }
            ?: error("Parcel does not belong to this shipment.")
        if (routeLegId != null) parcel.currentRouteLegId = routeLegId
        if (assignmentId != null) parcel.currentAssignmentId = assignmentId
        parcel.updatedAt = recordedAt
        events += ShipmentEvent(
            shipment = this,
            parcelId = parcel.id,
            status = status,
            code = "parcel.scanned",
            description = requireEventText(description, 500, "description"),
            actorUserId = actorUserId,
            hubId = hubId,
            locationName = locationName.trim().takeIf { it.isNotEmpty() }?.also {
                require(it.length <= 200) { "locationName must not exceed 200 characters." }
            },
            occurredAt = occurredAt,
            recordedAt = recordedAt,
        )
        updatedAt = recordedAt
    }

    private fun resolveStatus(items: List<Parcel>): ShipmentStatus {
        if (items.all { it.status == ParcelStatus.DELIVERED }) return ShipmentStatus.DELIVERED
        if (items.any { it.status == ParcelStatus.DELIVERED }) return ShipmentStatus.PARTIALLY_DELIVERED
        if (items.all { it.status == ParcelStatus.CANCELLED }) return ShipmentStatus.CANCELLED
        if (items.all { it.status == ParcelStatus.RETURNED }) return ShipmentStatus.RETURNED
        if (items.any { it.status == ParcelStatus.RETURNING }) return ShipmentStatus.RETURNING
        if (items.any { it.status == ParcelStatus.DELIVERY_FAILED }) return ShipmentStatus.DELIVERY_FAILED
        if (items.any { it.status == ParcelStatus.OUT_FOR_DELIVERY }) return ShipmentStatus.OUT_FOR_DELIVERY
        if (items.any { it.status == ParcelStatus.AT_DESTINATION_HUB }) return ShipmentStatus.AT_DESTINATION_HUB
        if (items.any { it.status == ParcelStatus.AT_TRANSIT_HUB }) return ShipmentStatus.IN_TRANSIT
        if (items.any { it.status == ParcelStatus.IN_TRANSIT }) return ShipmentStatus.IN_TRANSIT
        if (items.any { it.status == ParcelStatus.AT_ORIGIN_HUB }) return ShipmentStatus.AT_ORIGIN_HUB
        return if (items.any { it.status == ParcelStatus.PICKED_UP }) ShipmentStatus.PICKED_UP else ShipmentStatus.AWAITING_PICKUP
    }

    private fun requireEventText(value: String, maxLength: Int, field: String): String {
        val normalized = value.trim()
        require(normalized.isNotEmpty() && normalized.length <= maxLength) {
            "$field is required and must not exceed $maxLength characters."
        }
        return normalized
    }

    companion object {
        fun createFoundation(
            id: UUID,
            trackingCode: String,
            merchantId: UUID,
            createdByUserId: UUID,
            idempotencyKey: String,
            requestFingerprint: String,
            merchantReference: String?,
            pickupAddressId: UUID?,
            sender: ShipmentAddressSnapshot,
            recipient: ShipmentAddressSnapshot,
            shippingFeePayer: ShippingFeePayer,
            price: ShipmentPriceSnapshot,
            origin: ShipmentNetworkPointSnapshot,
            destination: ShipmentNetworkPointSnapshot,
            parcelDrafts: List<ParcelDraft>,
            note: String?,
            createdAt: Instant,
        ): Shipment {
            require(id != UUID(0, 0)) { "shipment id is required." }
            require(merchantId != UUID(0, 0) && createdByUserId != UUID(0, 0)) {
                "Merchant and creator identifiers are required."
            }
            require(price.quoteId != UUID(0, 0) && price.quoteRequestId != UUID(0, 0)) {
                "A valid pricing quote is required."
            }
            require(price.quoteExpiresAt.isAfter(createdAt)) { "Pricing quote must still be valid." }
            require(price.rateRevision > 0) { "rateRevision must be positive." }
            require(price.estimatedMinDays >= 0 && price.estimatedMaxDays >= price.estimatedMinDays) {
                "Pricing ETA range is invalid."
            }
            require(
                price.shippingFee == price.baseFee + price.additionalWeightFee +
                    price.areaSurcharge + price.codFee + price.insuranceFee,
            ) { "shippingFee must match the immutable fee breakdown." }
            require(parcelDrafts.size in 1..20) { "A shipment must contain between 1 and 20 parcels." }

            val normalizedTrackingCode = requireText(trackingCode, 32, "trackingCode").uppercase()
            val normalizedIdempotencyKey = requireText(idempotencyKey, 100, "idempotencyKey")
            require(normalizedIdempotencyKey.length >= 8) { "idempotencyKey must contain at least 8 characters." }
            val normalizedFingerprint = requestFingerprint.trim().uppercase()
            require(normalizedFingerprint.length == 64 && normalizedFingerprint.all { it in "0123456789ABCDEF" }) {
                "requestFingerprint must be a SHA-256 hexadecimal value."
            }

            val shipment = Shipment(
                id = id,
                trackingCode = normalizedTrackingCode,
                merchantId = merchantId,
                createdByUserId = createdByUserId,
                idempotencyKey = normalizedIdempotencyKey,
                requestFingerprint = normalizedFingerprint,
                merchantReference = optionalText(merchantReference, 100),
                pickupAddressId = pickupAddressId,
                sender = sender,
                recipient = recipient,
                shippingFeePayer = shippingFeePayer,
                quoteId = price.quoteId,
                quoteRequestId = price.quoteRequestId,
                ratePlanId = price.ratePlanId,
                rateCode = requireText(price.rateCode, 60, "rateCode"),
                rateRevision = price.rateRevision,
                rateVersion = requireText(price.rateVersion, 100, "rateVersion"),
                currency = requireText(price.currency, 3, "currency").uppercase(),
                serviceLevel = price.serviceLevel,
                pricingZone = price.pricingZone,
                baseFee = requireMoney(price.baseFee, "baseFee"),
                additionalWeightFee = requireMoney(price.additionalWeightFee, "additionalWeightFee"),
                areaSurcharge = requireMoney(price.areaSurcharge, "areaSurcharge"),
                codFee = requireMoney(price.codFee, "codFee"),
                insuranceFee = requireMoney(price.insuranceFee, "insuranceFee"),
                shippingFee = requireMoney(price.shippingFee, "shippingFee"),
                codAmount = requireMoney(price.codAmount, "codAmount"),
                declaredValue = requireMoney(price.declaredValue, "declaredValue"),
                recipientCollectAmount = Math.addExact(
                    price.codAmount,
                    if (shippingFeePayer == ShippingFeePayer.RECIPIENT) price.shippingFee else 0,
                ),
                estimatedMinDays = price.estimatedMinDays,
                estimatedMaxDays = price.estimatedMaxDays,
                quoteExpiresAt = price.quoteExpiresAt,
                originServiceAreaId = origin.serviceAreaId,
                originRegionId = origin.regionId,
                originHubId = origin.hubId,
                originHubCode = requireText(origin.hubCode, 60, "originHubCode"),
                originHubName = requireText(origin.hubName, 200, "originHubName"),
                originCoverageTier = origin.coverageTier,
                destinationServiceAreaId = destination.serviceAreaId,
                destinationRegionId = destination.regionId,
                destinationHubId = destination.hubId,
                destinationHubCode = requireText(destination.hubCode, 60, "destinationHubCode"),
                destinationHubName = requireText(destination.hubName, 200, "destinationHubName"),
                destinationCoverageTier = destination.coverageTier,
                note = optionalText(note, 1000),
                createdAt = createdAt,
                updatedAt = createdAt,
            )

            parcelDrafts.forEachIndexed { index, draft ->
                shipment.parcels += Parcel.create(
                    shipment = shipment,
                    code = "$normalizedTrackingCode-${(index + 1).toString().padStart(2, '0')}",
                    sequence = index + 1,
                    draft = draft,
                    createdAt = createdAt,
                )
            }
            shipment.events += ShipmentEvent.created(shipment, createdByUserId, createdAt)
            return shipment
        }

        private fun requireMoney(value: Long, field: String): Long {
            require(value >= 0) { "$field must not be negative." }
            return value
        }

        private fun requireText(value: String, maxLength: Int, field: String): String {
            val normalized = value.trim()
            require(normalized.isNotEmpty() && normalized.length <= maxLength) {
                "$field is required and must not exceed $maxLength characters."
            }
            return normalized
        }

        private fun optionalText(value: String?, maxLength: Int): String? =
            value?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(it.length <= maxLength) { "Text must not exceed $maxLength characters." }
            }
    }
}
