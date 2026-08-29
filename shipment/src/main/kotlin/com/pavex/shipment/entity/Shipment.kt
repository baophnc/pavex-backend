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
    var pricingZone: ShipmentPricingZone = ShipmentPricingZone.SAME_SERVICE_AREA,
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

    companion object {
        fun createFoundation(
            id: UUID,
            trackingCode: String,
            merchantId: UUID,
            createdByUserId: UUID,
            idempotencyKey: String,
            requestFingerprint: String,
            merchantReference: String?,
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
