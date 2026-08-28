package com.pavex.partners.service

import com.pavex.partners.dto.PickupAddressRequest
import com.pavex.partners.dto.PickupAddressResponse
import com.pavex.partners.entity.MerchantPickupAddress
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.MerchantPickupAddressRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class MerchantPickupAddressService(
    private val pickupAddresses: MerchantPickupAddressRepository,
    private val merchants: MerchantService,
    private val locations: LocationService,
) {
    @Transactional
    fun create(userId: UUID, request: PickupAddressRequest): PickupAddressResponse {
        val merchant = merchants.findActiveByUser(userId)
        validateLocation(request)
        val currentDefault = pickupAddresses.findFirstByMerchantIdAndIsDefaultTrue(merchant.id)
        val now = Instant.now()
        if (request.isDefault && currentDefault != null) {
            currentDefault.isDefault = false
            currentDefault.updatedAt = now
            pickupAddresses.saveAndFlush(currentDefault)
        }
        return pickupAddresses.save(
            MerchantPickupAddress(
                merchantId = merchant.id,
                name = request.name.trim(),
                contactName = request.contactName.trim(),
                contactPhone = request.contactPhone.trim(),
                addressLine = request.addressLine.trim(),
                provinceCode = request.provinceCode.trim(),
                wardCode = request.wardCode.trim(),
                isDefault = request.isDefault || currentDefault == null,
                createdAt = now,
                updatedAt = now,
            ),
        ).toResponse()
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID): List<PickupAddressResponse> {
        val merchant = merchants.findActiveByUser(userId)
        return pickupAddresses.findAllByMerchantIdOrderByIsDefaultDescCreatedAtAsc(merchant.id)
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID, id: UUID): PickupAddressResponse {
        val merchant = merchants.findActiveByUser(userId)
        return findOwned(merchant.id, id).toResponse()
    }

    @Transactional
    fun update(userId: UUID, id: UUID, request: PickupAddressRequest): PickupAddressResponse {
        val merchant = merchants.findActiveByUser(userId)
        val address = findOwned(merchant.id, id)
        validateLocation(request)
        val now = Instant.now()
        if (request.isDefault && !address.isDefault) {
            pickupAddresses.findFirstByMerchantIdAndIsDefaultTrue(merchant.id)?.let {
                it.isDefault = false
                it.updatedAt = now
                pickupAddresses.saveAndFlush(it)
            }
            address.isDefault = true
        }
        address.name = request.name.trim()
        address.contactName = request.contactName.trim()
        address.contactPhone = request.contactPhone.trim()
        address.addressLine = request.addressLine.trim()
        address.provinceCode = request.provinceCode.trim()
        address.wardCode = request.wardCode.trim()
        address.updatedAt = now
        return pickupAddresses.save(address).toResponse()
    }

    @Transactional
    fun delete(userId: UUID, id: UUID) {
        val merchant = merchants.findActiveByUser(userId)
        val address = findOwned(merchant.id, id)
        val wasDefault = address.isDefault
        pickupAddresses.delete(address)
        pickupAddresses.flush()
        if (wasDefault) {
            pickupAddresses.findFirstByMerchantIdOrderByCreatedAtAsc(merchant.id)?.let {
                it.isDefault = true
                it.updatedAt = Instant.now()
                pickupAddresses.save(it)
            }
        }
    }

    @Transactional
    fun setDefault(userId: UUID, id: UUID) {
        val merchant = merchants.findActiveByUser(userId)
        val address = findOwned(merchant.id, id)
        if (address.isDefault) return
        val now = Instant.now()
        pickupAddresses.findFirstByMerchantIdAndIsDefaultTrue(merchant.id)?.let {
            it.isDefault = false
            it.updatedAt = now
            pickupAddresses.saveAndFlush(it)
        }
        address.isDefault = true
        address.updatedAt = now
        pickupAddresses.save(address)
    }

    private fun validateLocation(request: PickupAddressRequest) {
        if (!locations.isValid(request.provinceCode.trim(), request.wardCode.trim())) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid_location",
                "Ward does not belong to the selected province.",
            )
        }
    }

    private fun findOwned(merchantId: UUID, id: UUID): MerchantPickupAddress =
        pickupAddresses.findByIdAndMerchantId(id, merchantId)
            ?: throw ApiException(
                HttpStatus.NOT_FOUND,
                "pickup_address_not_found",
                "Pickup address was not found.",
            )

    private fun MerchantPickupAddress.toResponse() = PickupAddressResponse(
        id, name, contactName, contactPhone, addressLine, provinceCode, wardCode,
        isDefault, createdAt, updatedAt,
    )
}
