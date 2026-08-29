package com.pavex.shipment.service

import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.contracts.partners.PartnerLocationSnapshot
import com.pavex.contracts.pricing.PricingQuoteServiceLevel
import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.contracts.pricing.PricingQuoteZone
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.client.PricingQuoteClient
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ParcelContentsRequest
import com.pavex.shipment.dto.ShipmentAddressRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.entity.ParcelDraft
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import com.pavex.shipment.entity.ShipmentCoverageTier
import com.pavex.shipment.entity.ShipmentNetworkPointSnapshot
import com.pavex.shipment.entity.ShipmentPriceSnapshot
import com.pavex.shipment.entity.ShipmentPricingZone
import com.pavex.shipment.entity.ShipmentServiceLevel
import com.pavex.shipment.entity.ShippingFeePayer
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.exception.ShipmentGoneException
import com.pavex.shipment.exception.ShipmentUnavailableException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

@Service
class CreateShipmentService(
    private val partners: PartnerShipmentClient,
    private val pricing: PricingQuoteClient,
    private val network: NetworkShipmentClient,
    private val writer: ShipmentCreationWriter,
    private val trackingCodes: ShipmentCodeGenerator,
    private val objectMapper: ObjectMapper,
) {
    fun create(
        actorUserId: UUID,
        idempotencyKeyHeader: String?,
        request: CreateShipmentRequest,
    ): CreateShipmentResult {
        val idempotencyKey = validateIdempotencyKey(idempotencyKeyHeader)
        val senderInput = normalizeAddress(request.sender)
        val recipientInput = normalizeAddress(request.recipient)
        val parcelInputs = normalizeParcels(request.parcels)
        val fingerprint = fingerprint(request, senderInput, recipientInput, parcelInputs)

        writer.findExisting(actorUserId, idempotencyKey)?.let {
            return replayOrConflict(it, fingerprint)
        }

        val capability = partners.orderCapability(actorUserId)
        val merchantId = capability.merchantId
        if (!capability.canCreateOrders || merchantId == null) {
            throw ShipmentForbiddenException(
                "merchant_not_active",
                "An active merchant profile is required to create shipments.",
            )
        }

        val now = Instant.now()
        val quote = pricing.getValid(request.quoteId)
        if (quote.quoteId != request.quoteId || !quote.expiresAt.isAfter(now)) {
            throw ShipmentGoneException(
                "quote_invalid_or_expired",
                "The selected pricing quote is missing or expired.",
            )
        }
        validateQuoteLocations(quote, senderInput, recipientInput)
        validateQuoteParcels(quote, parcelInputs)
        if (writer.isQuoteUsed(quote.quoteId)) {
            throw quoteAlreadyUsed()
        }

        val senderLocation = partners.resolveLocation(senderInput.provinceCode, senderInput.wardCode)
        val recipientLocation = partners.resolveLocation(recipientInput.provinceCode, recipientInput.wardCode)
        validateResolvedLocation(senderInput, senderLocation)
        validateResolvedLocation(recipientInput, recipientLocation)

        val sender = senderInput.toSnapshot(senderLocation)
        val recipient = recipientInput.toSnapshot(recipientLocation)
        val priceSnapshot = quote.toShipmentPriceSnapshot()
        val parcelDrafts = toParcelDrafts(quote, parcelInputs)
        val shipmentWeightKg = parcelDrafts.fold(BigDecimal.ZERO) { total, parcel -> total.add(parcel.actualWeightKg) }
        if (shipmentWeightKg.signum() <= 0) {
            throw InvalidShipmentRequestException(
                "invalid_shipment_weight",
                "Shipment weight must be positive.",
            )
        }

        repeat(MAXIMUM_INSERT_ATTEMPTS) { attemptIndex ->
            val shipmentId = UUID.randomUUID()
            var reservation: ShipmentNetworkReservationSnapshot? = null
            try {
                reservation = network.reserve(
                    ReserveShipmentNetworkRequest(
                        shipmentId = shipmentId,
                        actorUserId = actorUserId,
                        routeRevision = INITIAL_ROUTE_REVISION,
                        originProvinceCode = sender.provinceCode,
                        originWardCode = sender.wardCode,
                        destinationProvinceCode = recipient.provinceCode,
                        destinationWardCode = recipient.wardCode,
                        shipmentWeightKg = shipmentWeightKg,
                        readyAt = now,
                        expectedOriginServiceAreaId = quote.origin.serviceAreaId,
                        expectedOriginRegionId = quote.origin.regionId,
                        expectedOriginHubId = quote.origin.primaryHubId,
                        expectedDestinationServiceAreaId = quote.destination.serviceAreaId,
                        expectedDestinationRegionId = quote.destination.regionId,
                        expectedDestinationHubId = quote.destination.primaryHubId,
                    ),
                )
                validateReservation(quote, shipmentId, shipmentWeightKg, reservation)

                val shipment = Shipment.createFoundation(
                    id = shipmentId,
                    trackingCode = trackingCodes.create(now),
                    merchantId = merchantId,
                    createdByUserId = actorUserId,
                    idempotencyKey = idempotencyKey,
                    requestFingerprint = fingerprint,
                    merchantReference = request.merchantReference,
                    sender = sender,
                    recipient = recipient,
                    shippingFeePayer = request.shippingFeePayer,
                    price = priceSnapshot,
                    origin = reservation.origin.toShipmentSnapshot(),
                    destination = reservation.destination.toShipmentSnapshot(),
                    parcelDrafts = parcelDrafts,
                    note = request.note,
                    createdAt = now,
                )
                shipment.assignReservedRoute(
                    routeRevision = reservation.routeRevision,
                    capacityReservationBatchId = reservation.batchId,
                    reservedWeightKg = reservation.reservedWeightKg,
                    readyAt = reservation.readyAt,
                    estimatedDeliveryAt = reservation.estimatedDeliveryAt,
                    estimatedTransitMinutes = reservation.estimatedTransitMinutes,
                    distanceKm = reservation.distanceKm,
                    updatedAt = now,
                )

                return CreateShipmentResult(writer.persist(shipment), replayed = false)
            } catch (exception: DataIntegrityViolationException) {
                compensateIfNeeded(reservation, shipmentId, actorUserId, "Shipment persistence failed.")

                writer.findExisting(actorUserId, idempotencyKey)?.let {
                    return replayOrConflict(it, fingerprint)
                }
                if (writer.isQuoteUsed(quote.quoteId)) {
                    throw quoteAlreadyUsed()
                }
                if (attemptIndex == MAXIMUM_INSERT_ATTEMPTS - 1) {
                    throw ShipmentUnavailableException(
                        "tracking_code_allocation_failed",
                        "A unique PAVEX tracking code could not be allocated. Please retry the request.",
                    )
                }
            } catch (exception: RuntimeException) {
                compensateIfNeeded(reservation, shipmentId, actorUserId, "Shipment creation aborted.")
                throw exception
            }
        }

        error("Unreachable shipment creation state.")
    }

    private fun compensateIfNeeded(
        reservation: ShipmentNetworkReservationSnapshot?,
        shipmentId: UUID,
        actorUserId: UUID,
        reason: String,
    ) {
        if (reservation?.batchId != null) {
            network.releaseBestEffort(shipmentId, reservation.routeRevision, actorUserId, reason)
        }
    }

    private fun replayOrConflict(existing: ExistingShipmentCreation, fingerprint: String): CreateShipmentResult {
        if (existing.requestFingerprint != fingerprint) {
            throw ShipmentConflictException(
                "idempotency_conflict",
                "Idempotency-Key was already used for a different request.",
            )
        }
        return CreateShipmentResult(existing.response, replayed = true)
    }

    private fun validateQuoteLocations(
        quote: PricingQuoteSnapshot,
        sender: NormalizedAddress,
        recipient: NormalizedAddress,
    ) {
        val senderMatches = sender.provinceCode == quote.origin.provinceCode && sender.wardCode == quote.origin.wardCode
        val recipientMatches = recipient.provinceCode == quote.destination.provinceCode &&
            recipient.wardCode == quote.destination.wardCode
        if (!senderMatches || !recipientMatches) {
            throw InvalidShipmentRequestException(
                "quote_location_mismatch",
                "Sender and recipient locations must match the selected quote.",
            )
        }
    }

    private fun validateQuoteParcels(quote: PricingQuoteSnapshot, parcels: List<NormalizedParcel>) {
        if (quote.parcelCount != parcels.size || quote.parcels.size != parcels.size) {
            throw InvalidShipmentRequestException(
                "quote_parcel_mismatch",
                "Parcel contents must match the number of parcels in the selected quote.",
            )
        }
        val sequences = quote.parcels.map { it.sequence }.sorted()
        if (sequences != (1..parcels.size).toList()) {
            throw InvalidShipmentRequestException(
                "quote_parcel_mismatch",
                "The selected quote contains an invalid parcel sequence.",
            )
        }
    }

    private fun validateResolvedLocation(input: NormalizedAddress, resolved: PartnerLocationSnapshot) {
        if (input.provinceCode != resolved.provinceCode || input.wardCode != resolved.wardCode) {
            throw InvalidShipmentRequestException(
                "invalid_address",
                "Resolved province or ward does not match the shipment request.",
            )
        }
    }

    private fun validateReservation(
        quote: PricingQuoteSnapshot,
        shipmentId: UUID,
        shipmentWeightKg: BigDecimal,
        reservation: ShipmentNetworkReservationSnapshot,
    ) {
        val valid = reservation.shipmentId == shipmentId &&
            reservation.routeRevision == INITIAL_ROUTE_REVISION &&
            reservation.reservedWeightKg.compareTo(shipmentWeightKg) == 0 &&
            reservation.origin.serviceAreaId == quote.origin.serviceAreaId &&
            reservation.origin.regionId == quote.origin.regionId &&
            reservation.origin.hubId == quote.origin.primaryHubId &&
            reservation.destination.serviceAreaId == quote.destination.serviceAreaId &&
            reservation.destination.regionId == quote.destination.regionId &&
            reservation.destination.hubId == quote.destination.primaryHubId
        if (!valid) {
            throw InvalidShipmentRequestException(
                "network_reservation_mismatch",
                "Network reservation does not match the selected pricing quote.",
            )
        }
    }

    private fun normalizeAddress(address: ShipmentAddressRequest): NormalizedAddress = NormalizedAddress(
        contactName = requireText(address.contactName, 120, "contactName"),
        contactPhone = normalizeVietnamesePhone(address.contactPhone),
        addressLine = requireText(address.addressLine, 500, "addressLine"),
        provinceCode = requireText(address.provinceCode, 20, "provinceCode"),
        wardCode = requireText(address.wardCode, 20, "wardCode"),
    )

    private fun normalizeParcels(parcels: List<ParcelContentsRequest>): List<NormalizedParcel> {
        if (parcels.size !in 1..20) {
            throw InvalidShipmentRequestException(
                "invalid_parcel_contents",
                "A shipment must contain between 1 and 20 parcels.",
            )
        }
        return parcels.map {
            if (it.quantity !in 1..10_000) {
                throw InvalidShipmentRequestException(
                    "invalid_parcel_contents",
                    "Every parcel requires a description and a valid quantity.",
                )
            }
            NormalizedParcel(
                description = requireText(it.description, 500, "description"),
                quantity = it.quantity,
                category = it.category,
                isFragile = it.isFragile,
            )
        }
    }

    private fun fingerprint(
        request: CreateShipmentRequest,
        sender: NormalizedAddress,
        recipient: NormalizedAddress,
        parcels: List<NormalizedParcel>,
    ): String {
        val bytes = objectMapper.writeValueAsBytes(
            ShipmentRequestFingerprint(
                quoteId = request.quoteId,
                merchantReference = request.merchantReference?.trim(),
                sender = sender,
                recipient = recipient,
                shippingFeePayer = request.shippingFeePayer,
                parcels = parcels,
                note = request.note?.trim(),
            ),
        )
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    private fun validateIdempotencyKey(value: String?): String {
        if (value == null || value.length !in 8..100 || value.any { it.code > 127 || (!it.isLetterOrDigit() && it !in "-_.:") }) {
            if (value == null) {
                throw InvalidShipmentRequestException("idempotency_key_required", "Idempotency-Key header is required.")
            }
            throw InvalidShipmentRequestException(
                "invalid_idempotency_key",
                "Idempotency-Key must contain 8 to 100 safe characters.",
            )
        }
        return value
    }

    private fun normalizeVietnamesePhone(value: String): String {
        var normalized = value.filter { it.isDigit() || it == '+' }
        if (normalized.startsWith("+84")) normalized = "0${normalized.drop(3)}"
        if (normalized.length != 10 || !normalized.startsWith('0') || !normalized.all(Char::isDigit)) {
            throw InvalidShipmentRequestException("invalid_address", "contactPhone must be a valid Vietnamese phone number.")
        }
        return normalized
    }

    private fun requireText(value: String, maxLength: Int, field: String): String {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.length > maxLength) {
            throw InvalidShipmentRequestException(
                if (field == "description") "invalid_parcel_contents" else "invalid_address",
                "$field is required and must not exceed $maxLength characters.",
            )
        }
        return normalized
    }

    private fun toParcelDrafts(quote: PricingQuoteSnapshot, parcels: List<NormalizedParcel>): List<ParcelDraft> =
        quote.parcels.sortedBy { it.sequence }.zip(parcels).map { (quoted, contents) ->
            ParcelDraft(
                description = contents.description,
                quantity = contents.quantity,
                category = contents.category,
                isFragile = contents.isFragile,
                actualWeightKg = quoted.actualWeightKg,
                volumetricWeightKg = quoted.volumetricWeightKg,
                chargeableWeightKg = quoted.chargeableWeightKg,
                lengthCm = quoted.lengthCm,
                widthCm = quoted.widthCm,
                heightCm = quoted.heightCm,
            )
        }

    private fun PricingQuoteSnapshot.toShipmentPriceSnapshot() = ShipmentPriceSnapshot(
        quoteId = quoteId,
        quoteRequestId = requestId,
        ratePlanId = ratePlanId,
        rateCode = rateCode,
        rateRevision = rateRevision,
        rateVersion = rateVersion,
        currency = currency,
        serviceLevel = when (serviceLevel) {
            PricingQuoteServiceLevel.STANDARD -> ShipmentServiceLevel.STANDARD
            PricingQuoteServiceLevel.EXPRESS -> ShipmentServiceLevel.EXPRESS
        },
        pricingZone = when (pricingZone) {
            PricingQuoteZone.SAME_SERVICE_AREA -> ShipmentPricingZone.SAME_SERVICE_AREA
            PricingQuoteZone.SAME_REGION -> ShipmentPricingZone.SAME_REGION
            PricingQuoteZone.CROSS_REGION -> ShipmentPricingZone.CROSS_REGION
        },
        baseFee = fees.baseFee,
        additionalWeightFee = fees.additionalWeightFee,
        areaSurcharge = fees.areaSurcharge,
        codFee = fees.codFee,
        insuranceFee = fees.insuranceFee,
        shippingFee = fees.total,
        codAmount = codAmount,
        declaredValue = declaredValue,
        estimatedMinDays = estimatedMinDays,
        estimatedMaxDays = estimatedMaxDays,
        quoteExpiresAt = expiresAt,
    )

    private fun com.pavex.contracts.network.ShipmentNetworkPointSnapshot.toShipmentSnapshot() = ShipmentNetworkPointSnapshot(
        serviceAreaId = serviceAreaId,
        regionId = regionId,
        hubId = hubId,
        hubCode = hubCode,
        hubName = hubName,
        coverageTier = ShipmentCoverageTier.valueOf(coverageTier.name),
    )

    private fun quoteAlreadyUsed() = ShipmentConflictException(
        "quote_already_used",
        "The selected pricing quote has already been used.",
    )

    companion object {
        private const val MAXIMUM_INSERT_ATTEMPTS = 5
        private const val INITIAL_ROUTE_REVISION = 1
    }
}

data class CreateShipmentResult(
    val shipment: ShipmentCreatedResponse,
    val replayed: Boolean,
)

private data class NormalizedAddress(
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
) {
    fun toSnapshot(location: PartnerLocationSnapshot): ShipmentAddressSnapshot = ShipmentAddressSnapshot.create(
        contactName = contactName,
        contactPhone = contactPhone,
        addressLine = addressLine,
        provinceCode = location.provinceCode,
        provinceName = location.provinceName,
        wardCode = location.wardCode,
        wardName = location.wardName,
    )
}

private data class NormalizedParcel(
    val description: String,
    val quantity: Int,
    val category: com.pavex.shipment.entity.GoodsCategory,
    val isFragile: Boolean,
)

private data class ShipmentRequestFingerprint(
    val quoteId: UUID,
    val merchantReference: String?,
    val sender: NormalizedAddress,
    val recipient: NormalizedAddress,
    val shippingFeePayer: ShippingFeePayer,
    val parcels: List<NormalizedParcel>,
    val note: String?,
)
