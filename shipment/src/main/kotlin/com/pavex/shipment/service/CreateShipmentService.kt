package com.pavex.shipment.service

import com.pavex.common.contact.VietnamesePhone
import com.pavex.contracts.network.ReserveShipmentNetworkRequest
import com.pavex.contracts.network.ShipmentNetworkReservationSnapshot
import com.pavex.contracts.operations.OperationsAssignmentStatus
import com.pavex.contracts.operations.OperationsAssignmentType
import com.pavex.contracts.operations.OperationsCapability
import com.pavex.contracts.operations.ShipmentOperationsPlanResponse
import com.pavex.contracts.partners.PartnerLocationSnapshot
import com.pavex.contracts.pricing.PricingQuoteServiceLevel
import com.pavex.contracts.pricing.PricingQuoteSnapshot
import com.pavex.contracts.pricing.PricingQuoteZone
import com.pavex.shipment.client.NetworkShipmentClient
import com.pavex.shipment.client.OperationsShipmentClient
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.client.PricingQuoteClient
import com.pavex.shipment.dto.CreateCounterShipmentRequest
import com.pavex.shipment.dto.CreateShipmentRequest
import com.pavex.shipment.dto.ParcelContentsRequest
import com.pavex.shipment.dto.ShipmentAddressRequest
import com.pavex.shipment.dto.ShipmentCreatedResponse
import com.pavex.shipment.entity.CounterAcceptance
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
import com.pavex.shipment.exception.ShipmentApiException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentForbiddenException
import com.pavex.shipment.exception.ShipmentGoneException
import com.pavex.shipment.exception.ShipmentUnavailableException
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class CreateShipmentService(
    private val partners: PartnerShipmentClient,
    private val pricing: PricingQuoteClient,
    private val network: NetworkShipmentClient,
    private val operations: OperationsShipmentClient,
    private val networkConfirmations: ShipmentNetworkConfirmationService,
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
        val recipientInput = normalizeAddress(request.recipient)
        val parcelInputs = normalizeParcels(request.parcels)
        val fingerprint = fingerprint(request, recipientInput, parcelInputs)

        writer.findExisting(actorUserId, idempotencyKey)?.let {
            return replayOrConflict(it, fingerprint)
        }

        val capability = partners.orderCapability(actorUserId)
        val merchantId = capability.merchantId
        if (!capability.canCreateOrders || merchantId == null) {
            throw orderRestriction(capability.restrictions)
        }
        val merchantReference = request.merchantReference?.trim()?.takeIf { it.isNotEmpty() }
        merchantReference?.let { requireUnusedMerchantReference(merchantId, it) }
        val senderInput = pickupSender(merchantId, request.pickupAddressId)

        return book(
            Booking(
                actorUserId = actorUserId,
                idempotencyKey = idempotencyKey,
                fingerprint = fingerprint,
                quoteId = request.quoteId,
                sender = senderInput,
                recipient = recipientInput,
                parcels = parcelInputs,
                shippingFeePayer = request.shippingFeePayer,
                note = request.note,
                merchantId = merchantId,
                merchantReference = request.merchantReference,
                pickupAddressId = request.pickupAddressId,
                counter = null,
            ),
        )
    }

    /**
     * Hub staff book a walk-in customer's shipment at their hub's counter. The sender is typed in
     * (no shop, no pickup address), the parcels are already in the hub, and the quote must start at
     * this hub; when the sender pays, the cash taken must be exactly the shipping fee.
     */
    /** [allHubs]: head office books at any hub's counter; hub staff only at a hub they work at. */
    fun createAtCounter(
        actorUserId: UUID,
        idempotencyKeyHeader: String?,
        request: CreateCounterShipmentRequest,
        allHubs: Boolean = false,
    ): CreateShipmentResult {
        val idempotencyKey = validateIdempotencyKey(idempotencyKeyHeader)
        val senderInput = normalizeAddress(request.sender)
        val recipientInput = normalizeAddress(request.recipient)
        val parcelInputs = normalizeParcels(request.parcels)
        val fingerprint = counterFingerprint(request, senderInput, recipientInput, parcelInputs)

        writer.findExisting(actorUserId, idempotencyKey)?.let {
            return replayOrConflict(it, fingerprint)
        }

        if (!allHubs && !operations.canOperateAtHub(actorUserId, request.hubId, OperationsCapability.PARCEL_SCANNING)) {
            throw ShipmentForbiddenException(
                "counter_hub_forbidden",
                "The current user does not serve the counter of this hub.",
            )
        }

        return book(
            Booking(
                actorUserId = actorUserId,
                idempotencyKey = idempotencyKey,
                fingerprint = fingerprint,
                quoteId = request.quoteId,
                sender = senderInput,
                recipient = recipientInput,
                parcels = parcelInputs,
                shippingFeePayer = request.shippingFeePayer,
                note = request.note,
                merchantId = null,
                merchantReference = null,
                pickupAddressId = null,
                counter = CounterBooking(request.hubId, request.cashReceived),
            ),
        )
    }

    /**
     * Prices, routes, plans and stores one shipment. The network reservation and operations plan
     * are tied to the shipment id, so they are made exactly once and released again if anything
     * after them fails; only the tracking code is regenerated if it collides.
     */
    private fun book(booking: Booking): CreateShipmentResult {
        val actorUserId = booking.actorUserId
        val counter = booking.counter
        val merchantReference = booking.merchantReference?.trim()?.takeIf { it.isNotEmpty() }

        val now = Instant.now()
        val quote = pricing.getValid(booking.quoteId)
        if (quote.quoteId != booking.quoteId || !quote.expiresAt.isAfter(now)) {
            throw ShipmentGoneException(
                "quote_invalid_or_expired",
                "The selected pricing quote is missing or expired.",
            )
        }
        if (counter == null) {
            validateQuoteLocations(quote, booking.sender, booking.recipient)
        } else {
            validateCounterQuote(quote, counter, booking.recipient, booking.shippingFeePayer)
        }
        validateQuoteParcels(quote, booking.parcels)
        if (writer.isQuoteUsed(quote.quoteId)) {
            throw quoteAlreadyUsed()
        }

        val senderLocation = partners.resolveLocation(booking.sender.provinceCode, booking.sender.wardCode)
        val recipientLocation = partners.resolveLocation(booking.recipient.provinceCode, booking.recipient.wardCode)
        validateResolvedLocation(booking.sender, senderLocation)
        validateResolvedLocation(booking.recipient, recipientLocation)

        val sender = booking.sender.toSnapshot(senderLocation)
        val recipient = booking.recipient.toSnapshot(recipientLocation)
        val priceSnapshot = quote.toShipmentPriceSnapshot()
        val parcelDrafts = toParcelDrafts(quote, booking.parcels)
        val shipmentWeightKg = parcelDrafts.fold(BigDecimal.ZERO) { total, parcel -> total.add(parcel.actualWeightKg) }
        if (shipmentWeightKg.signum() <= 0) {
            throw InvalidShipmentRequestException(
                "invalid_shipment_weight",
                "Shipment weight must be positive.",
            )
        }

        val shipmentId = UUID.randomUUID()
        // If reserve() itself fails nothing is held; an ambiguous failure (e.g. a timeout
        // after Network committed) is cleaned up by Network's unconfirmed-reservation expiry.
        // The route starts where the quote starts: the shop's pickup ward, or the counter hub.
        val reservation = network.reserve(
            ReserveShipmentNetworkRequest(
                shipmentId = shipmentId,
                actorUserId = actorUserId,
                routeRevision = INITIAL_ROUTE_REVISION,
                originProvinceCode = quote.origin.provinceCode,
                originWardCode = quote.origin.wardCode,
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
        val plan = try {
            validateReservation(quote, shipmentId, shipmentWeightKg, reservation)
            operations.plan(shipmentId, reservation, startsAtOriginHub = counter != null)
        } catch (exception: RuntimeException) {
            compensateIfNeeded(reservation, operationsPlanned = false, shipmentId, actorUserId, "Shipment creation aborted.")
            throw exception
        }
        val operationsPlanned = true

        repeat(MAXIMUM_TRACKING_CODE_ATTEMPTS) {
            try {
                val shipment = Shipment.createFoundation(
                    id = shipmentId,
                    trackingCode = trackingCodes.create(now),
                    merchantId = booking.merchantId,
                    createdByUserId = actorUserId,
                    idempotencyKey = booking.idempotencyKey,
                    requestFingerprint = booking.fingerprint,
                    merchantReference = booking.merchantReference,
                    pickupAddressId = booking.pickupAddressId,
                    sender = sender,
                    recipient = recipient,
                    shippingFeePayer = booking.shippingFeePayer,
                    price = priceSnapshot,
                    origin = reservation.origin.toShipmentSnapshot(),
                    destination = reservation.destination.toShipmentSnapshot(),
                    parcelDrafts = parcelDrafts,
                    note = booking.note,
                    createdAt = now,
                    counter = counter?.let { CounterAcceptance(it.cashReceived) },
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

                val created = writer.persist(shipment)
                if (reservation.batchId != null) {
                    networkConfirmations.confirmBestEffort(shipmentId, reservation.routeRevision, actorUserId)
                }
                if (counter != null) {
                    finishCounterHandling(shipmentId, plan, actorUserId, counter.hubId)
                }
                return CreateShipmentResult(created, replayed = false)
            } catch (exception: DataIntegrityViolationException) {
                // A concurrent request with the same Idempotency-Key or quote won the race:
                // release what this request reserved and answer like that request did.
                writer.findExisting(actorUserId, booking.idempotencyKey)?.let {
                    compensateIfNeeded(reservation, operationsPlanned, shipmentId, actorUserId, "Duplicate shipment request.")
                    return replayOrConflict(it, booking.fingerprint)
                }
                if (writer.isQuoteUsed(quote.quoteId)) {
                    compensateIfNeeded(reservation, operationsPlanned, shipmentId, actorUserId, "Quote already used.")
                    throw quoteAlreadyUsed()
                }
                val merchantId = booking.merchantId
                if (
                    merchantId != null && merchantReference != null &&
                    writer.shipmentWithMerchantReference(merchantId, merchantReference) != null
                ) {
                    compensateIfNeeded(reservation, operationsPlanned, shipmentId, actorUserId, "Merchant reference already used.")
                    requireUnusedMerchantReference(merchantId, merchantReference)
                }
                // Otherwise the random tracking code collided; retry with a new one.
            } catch (exception: RuntimeException) {
                compensateIfNeeded(reservation, operationsPlanned, shipmentId, actorUserId, "Shipment persistence failed.")
                throw exception
            }
        }

        compensateIfNeeded(reservation, operationsPlanned, shipmentId, actorUserId, "Tracking code allocation failed.")
        throw ShipmentUnavailableException(
            "tracking_code_allocation_failed",
            "A unique PAVEX tracking code could not be allocated. Please retry the request.",
        )
    }

    /**
     * The clerk who booked the counter shipment received the parcels into the hub, so the origin-hub
     * handling is theirs and already done; this opens the first line-haul leg. Best effort: the
     * shipment is saved, and an inbound scan at the hub can still take the handling over and finish it.
     */
    private fun finishCounterHandling(
        shipmentId: UUID,
        plan: ShipmentOperationsPlanResponse,
        actorUserId: UUID,
        hubId: UUID,
    ) {
        val handling = plan.assignments.firstOrNull {
            it.type == OperationsAssignmentType.ORIGIN_HUB_HANDLING && it.hubId == hubId
        } ?: return
        try {
            val claimed = if (handling.status in HANDLING_TAKEN_STATUSES) {
                handling
            } else {
                operations.claimHubHandling(shipmentId, handling.id, actorUserId, OperationsAssignmentType.ORIGIN_HUB_HANDLING, hubId)
            }
            if (claimed.status != OperationsAssignmentStatus.COMPLETED) {
                operations.completeAssignment(
                    shipmentId = shipmentId,
                    assignmentId = claimed.id,
                    actorUserId = actorUserId,
                    expectedType = OperationsAssignmentType.ORIGIN_HUB_HANDLING,
                    hubId = hubId,
                    note = "Parcels were handed in at the hub counter.",
                )
            }
        } catch (exception: ShipmentApiException) {
            log.warn(
                "Origin handling of counter shipment {} was not finished ({}); an inbound scan can finish it.",
                shipmentId,
                exception.code,
            )
        }
    }

    /**
     * A counter quote starts at the counter hub (it is priced from the hub's own ward) and ends at
     * the recipient; the cash taken is the whole fee when the sender pays, nothing otherwise.
     */
    private fun validateCounterQuote(
        quote: PricingQuoteSnapshot,
        counter: CounterBooking,
        recipient: NormalizedAddress,
        shippingFeePayer: ShippingFeePayer,
    ) {
        if (quote.origin.primaryHubId != counter.hubId) {
            throw InvalidShipmentRequestException(
                "counter_quote_hub_mismatch",
                "The selected quote must start at the counter's hub.",
            )
        }
        val recipientMatches = recipient.provinceCode == quote.destination.provinceCode &&
            recipient.wardCode == quote.destination.wardCode
        if (!recipientMatches) {
            throw InvalidShipmentRequestException(
                "quote_location_mismatch",
                "The recipient location must match the selected quote.",
            )
        }
        val expectedCash = if (shippingFeePayer == ShippingFeePayer.SENDER) quote.fees.total else 0
        if (counter.cashReceived != expectedCash) {
            throw InvalidShipmentRequestException(
                "counter_cash_mismatch",
                "The cash taken at the counter must be $expectedCash VND for this shipment.",
            )
        }
    }

    /** Names the reason a merchant cannot create orders, most fundamental first, so it can be fixed. */
    private fun orderRestriction(restrictions: List<String>): ShipmentForbiddenException {
        val (code, message) = when {
            "MERCHANT_REQUIRED" in restrictions -> "merchant_required" to "A merchant profile is required to create shipments."
            "MERCHANT_SUSPENDED" in restrictions -> "merchant_suspended" to "The merchant is suspended and cannot create shipments."
            "MERCHANT_NOT_ACTIVE" in restrictions -> "merchant_not_active" to "The merchant profile is not active yet."
            "IDENTITY_NOT_ELIGIBLE" in restrictions -> "identity_not_eligible" to "The account is not eligible to create shipments."
            "DEFAULT_PICKUP_REQUIRED" in restrictions -> "pickup_address_required" to "Set up a default pickup address before creating shipments."
            else -> "merchant_not_active" to "An active merchant profile is required to create shipments."
        }
        return ShipmentForbiddenException(code, message)
    }

    private fun compensateIfNeeded(
        reservation: ShipmentNetworkReservationSnapshot?,
        operationsPlanned: Boolean,
        shipmentId: UUID,
        actorUserId: UUID,
        reason: String,
    ) {
        if (operationsPlanned) {
            operations.cancelBestEffort(shipmentId, reason)
        }
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

    /**
     * A merchant reference identifies one live order, so re-submitting the same order (for example
     * after reloading the page) does not create a second shipment. Cancelled shipments free it.
     */
    private fun requireUnusedMerchantReference(merchantId: UUID, reference: String) {
        writer.shipmentWithMerchantReference(merchantId, reference)?.let { trackingCode ->
            throw ShipmentConflictException(
                "merchant_reference_duplicate",
                "Merchant reference '$reference' is already used by shipment $trackingCode.",
            )
        }
    }

    /**
     * The sender is the merchant's own saved pickup address, never an address typed into the
     * request: Partners only returns addresses that belong to [merchantId].
     */
    private fun pickupSender(merchantId: UUID, pickupAddressId: UUID): NormalizedAddress {
        val pickup = partners.pickupAddress(merchantId, pickupAddressId)
            ?: throw InvalidShipmentRequestException(
                "pickup_address_not_found",
                "The pickup address was not found among this merchant's pickup addresses.",
            )
        return try {
            normalizeAddress(
                ShipmentAddressRequest(
                    contactName = pickup.contactName,
                    contactPhone = pickup.contactPhone,
                    addressLine = pickup.addressLine,
                    provinceCode = pickup.provinceCode,
                    wardCode = pickup.wardCode,
                ),
            )
        } catch (_: InvalidShipmentRequestException) {
            throw InvalidShipmentRequestException(
                "pickup_address_invalid",
                "The pickup address cannot be used as a sender; update its contact phone or address.",
            )
        }
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
        recipient: NormalizedAddress,
        parcels: List<NormalizedParcel>,
    ): String {
        val bytes = objectMapper.writeValueAsBytes(
            ShipmentRequestFingerprint(
                quoteId = request.quoteId,
                merchantReference = request.merchantReference?.trim(),
                pickupAddressId = request.pickupAddressId,
                recipient = recipient,
                shippingFeePayer = request.shippingFeePayer,
                parcels = parcels,
                note = request.note?.trim(),
            ),
        )
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    private fun counterFingerprint(
        request: CreateCounterShipmentRequest,
        sender: NormalizedAddress,
        recipient: NormalizedAddress,
        parcels: List<NormalizedParcel>,
    ): String {
        val bytes = objectMapper.writeValueAsBytes(
            CounterShipmentRequestFingerprint(
                hubId = request.hubId,
                quoteId = request.quoteId,
                sender = sender,
                recipient = recipient,
                shippingFeePayer = request.shippingFeePayer,
                parcels = parcels,
                note = request.note?.trim(),
                cashReceived = request.cashReceived,
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

    private fun normalizeVietnamesePhone(value: String): String =
        VietnamesePhone.normalize(value)
            ?: throw InvalidShipmentRequestException("invalid_address", "contactPhone must be a valid Vietnamese phone number.")

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
            PricingQuoteZone.SAME_PROVINCE -> ShipmentPricingZone.SAME_PROVINCE
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
        private const val MAXIMUM_TRACKING_CODE_ATTEMPTS = 5
        private const val INITIAL_ROUTE_REVISION = 1
        private val HANDLING_TAKEN_STATUSES = setOf(
            OperationsAssignmentStatus.ACCEPTED,
            OperationsAssignmentStatus.IN_PROGRESS,
            OperationsAssignmentStatus.COMPLETED,
        )
        private val log = LoggerFactory.getLogger(CreateShipmentService::class.java)
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
    val pickupAddressId: UUID,
    val recipient: NormalizedAddress,
    val shippingFeePayer: ShippingFeePayer,
    val parcels: List<NormalizedParcel>,
    val note: String?,
)

/** What one booking needs, whether a shop created it on the web or staff booked it at a counter. */
private class Booking(
    val actorUserId: UUID,
    val idempotencyKey: String,
    val fingerprint: String,
    val quoteId: UUID,
    val sender: NormalizedAddress,
    val recipient: NormalizedAddress,
    val parcels: List<NormalizedParcel>,
    val shippingFeePayer: ShippingFeePayer,
    val note: String?,
    val merchantId: UUID?,
    val merchantReference: String?,
    val pickupAddressId: UUID?,
    val counter: CounterBooking?,
)

private data class CounterBooking(
    val hubId: UUID,
    val cashReceived: Long,
)

private data class CounterShipmentRequestFingerprint(
    val hubId: UUID,
    val quoteId: UUID,
    val sender: NormalizedAddress,
    val recipient: NormalizedAddress,
    val shippingFeePayer: ShippingFeePayer,
    val parcels: List<NormalizedParcel>,
    val note: String?,
    val cashReceived: Long,
)
