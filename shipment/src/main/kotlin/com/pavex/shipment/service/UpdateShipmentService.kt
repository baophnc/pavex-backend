package com.pavex.shipment.service

import com.pavex.common.contact.VietnamesePhone
import com.pavex.common.security.HubAccessScope
import com.pavex.shipment.client.PartnerShipmentClient
import com.pavex.shipment.dto.AdminShipmentDetailResponse
import com.pavex.shipment.dto.AdminUpdateShipmentRequest
import com.pavex.shipment.dto.ShipmentDetailResponse
import com.pavex.shipment.dto.UpdateShipmentRequest
import com.pavex.shipment.entity.ParcelStage
import com.pavex.shipment.entity.ParcelStatus
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentAddressSnapshot
import com.pavex.shipment.entity.ShipmentDetailsChange
import com.pavex.shipment.entity.ShipmentLifecycleStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentConflictException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.ShipmentRepository
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * Edits that keep the price and the route: the merchant's own shipment before pickup, and the
 * recipient's contact details by staff at any time before the parcels are finished. Changing the
 * ward, COD or parcels means cancelling and creating the shipment again.
 */
@Service
class UpdateShipmentService(
    private val shipments: ShipmentRepository,
    private val partners: PartnerShipmentClient,
    private val adminQueries: AdminShipmentQueryService,
    private val clock: Clock,
) {
    @Transactional
    fun updateOwn(actorUserId: UUID, rawTrackingCode: String, request: UpdateShipmentRequest): ShipmentDetailResponse {
        val shipment = shipments.findByTrackingCodeIgnoreCaseAndCreatedByUserId(trackingCode(rawTrackingCode), actorUserId)
            ?: throw notFound()
        requireActive(shipment)
        if (shipment.parcels.any { it.status != ParcelStatus.AWAITING_PICKUP }) {
            throw ShipmentConflictException(
                "shipment_already_picked_up",
                "The shipment was already picked up; ask Pavex support to change it.",
            )
        }
        request.merchantReference?.trim()?.takeIf(String::isNotEmpty)?.let { reference ->
            shipments.findActiveTrackingCodesByMerchantReference(shipment.merchantId, reference)
                .firstOrNull { it != shipment.trackingCode }
                ?.let { other ->
                    throw ShipmentConflictException(
                        "merchant_reference_duplicate",
                        "Merchant reference '$reference' is already used by shipment $other.",
                    )
                }
        }
        val sender = request.pickupAddressId
            ?.takeIf { it != shipment.pickupAddressId }
            ?.let { pickupSender(shipment, it) }

        apply(shipment, actorUserId, null) {
            ShipmentDetailsChange(
                recipientName = request.recipientName,
                recipientPhone = request.recipientPhone,
                recipientAddressLine = request.recipientAddressLine,
                note = request.note,
                merchantReference = request.merchantReference,
                pickupAddressId = request.pickupAddressId,
                sender = sender,
            )
        }
        return ShipmentResponseMapper.detail(shipment)
    }

    @Transactional
    fun updateAsStaff(
        scope: HubAccessScope,
        actorUserId: UUID,
        rawTrackingCode: String,
        request: AdminUpdateShipmentRequest,
    ): AdminShipmentDetailResponse {
        val shipment = adminQueries.findInScope(scope, rawTrackingCode)
        requireActive(shipment)
        if (shipment.parcels.all { it.stage in FINISHED }) {
            throw ShipmentConflictException("shipment_finished", "Every parcel is already delivered, returned or cancelled.")
        }
        apply(shipment, actorUserId, request.reason) {
            ShipmentDetailsChange(
                recipientName = request.recipientName,
                recipientPhone = request.recipientPhone,
                recipientAddressLine = request.recipientAddressLine,
                note = request.note,
            )
        }
        return adminQueries.get(scope, shipment.trackingCode)
    }

    private fun apply(shipment: Shipment, actorUserId: UUID, reason: String?, change: () -> ShipmentDetailsChange) {
        val changed = try {
            shipment.updateDetails(change(), actorUserId, reason, clock.instant())
        } catch (exception: IllegalArgumentException) {
            throw InvalidShipmentRequestException("invalid_shipment_update", exception.message ?: "The shipment update is invalid.")
        }
        if (changed.isEmpty()) return
        try {
            shipments.saveAndFlush(shipment)
        } catch (_: ObjectOptimisticLockingFailureException) {
            throw ShipmentConflictException("shipment_changed", "The shipment changed meanwhile; reload it and try again.")
        }
    }

    /** The merchant's saved pickup point as the new sender; it must stay in the shipment's ward. */
    private fun pickupSender(shipment: Shipment, pickupAddressId: UUID): ShipmentAddressSnapshot {
        val pickup = partners.pickupAddress(shipment.merchantId, pickupAddressId)
            ?: throw InvalidShipmentRequestException(
                "pickup_address_not_found",
                "The pickup address was not found among this merchant's pickup addresses.",
            )
        if (pickup.provinceCode != shipment.sender.provinceCode || pickup.wardCode != shipment.sender.wardCode) {
            throw InvalidShipmentRequestException(
                "pickup_address_area_changed",
                "The new pickup point is in another ward; cancel and create the shipment again.",
            )
        }
        val phone = VietnamesePhone.normalize(pickup.contactPhone)
        if (phone == null || pickup.contactName.isBlank() || pickup.addressLine.isBlank()) {
            throw InvalidShipmentRequestException(
                "pickup_address_invalid",
                "The pickup address cannot be used as a sender; update its contact phone or address.",
            )
        }
        return ShipmentAddressSnapshot(
            contactName = pickup.contactName.trim(),
            contactPhone = phone,
            addressLine = pickup.addressLine.trim(),
            provinceCode = shipment.sender.provinceCode,
            provinceName = shipment.sender.provinceName,
            wardCode = shipment.sender.wardCode,
            wardName = shipment.sender.wardName,
        )
    }

    private fun requireActive(shipment: Shipment) {
        if (shipment.lifecycleStatus != ShipmentLifecycleStatus.ACTIVE) {
            throw ShipmentConflictException("shipment_closed", "Only active shipments can be changed.")
        }
    }

    private fun trackingCode(raw: String) =
        ShipmentCodeNormalizer.normalizeOrNull(raw)?.takeIf { !it.contains('-') } ?: throw notFound()

    private fun notFound() = ShipmentNotFoundException("shipment_not_found", "Shipment was not found.")

    private companion object {
        val FINISHED = setOf(ParcelStage.DELIVERED, ParcelStage.RETURNED, ParcelStage.CANCELLED)
    }
}
