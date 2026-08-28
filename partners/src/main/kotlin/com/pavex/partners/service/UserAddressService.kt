package com.pavex.partners.service

import com.pavex.partners.dto.AddressRequest
import com.pavex.partners.dto.AddressResponse
import com.pavex.partners.entity.UserAddress
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.UserAddressRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserAddressService(
    private val addresses: UserAddressRepository,
    private val locations: LocationService,
) {
    @Transactional
    fun create(userId: UUID, request: AddressRequest): AddressResponse {
        validateLocation(request.provinceCode.trim(), request.wardCode.trim())
        val currentDefault = addresses.findFirstByUserIdAndIsDefaultTrue(userId)
        val now = Instant.now()
        if (request.isDefault && currentDefault != null) {
            currentDefault.isDefault = false
            currentDefault.updatedAt = now
            addresses.saveAndFlush(currentDefault)
        }
        return addresses.save(
            UserAddress(
                userId = userId,
                label = request.label.trim(),
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
    fun list(userId: UUID): List<AddressResponse> =
        addresses.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(userId).map { it.toResponse() }

    @Transactional(readOnly = true)
    fun get(userId: UUID, id: UUID): AddressResponse = findOwned(userId, id).toResponse()

    @Transactional
    fun update(userId: UUID, id: UUID, request: AddressRequest): AddressResponse {
        val address = findOwned(userId, id)
        validateLocation(request.provinceCode.trim(), request.wardCode.trim())
        val now = Instant.now()
        if (request.isDefault && !address.isDefault) {
            addresses.findFirstByUserIdAndIsDefaultTrue(userId)?.let {
                it.isDefault = false
                it.updatedAt = now
                addresses.saveAndFlush(it)
            }
            address.isDefault = true
        }
        address.label = request.label.trim()
        address.contactName = request.contactName.trim()
        address.contactPhone = request.contactPhone.trim()
        address.addressLine = request.addressLine.trim()
        address.provinceCode = request.provinceCode.trim()
        address.wardCode = request.wardCode.trim()
        address.updatedAt = now
        return addresses.save(address).toResponse()
    }

    @Transactional
    fun delete(userId: UUID, id: UUID) {
        val address = findOwned(userId, id)
        val wasDefault = address.isDefault
        addresses.delete(address)
        addresses.flush()
        if (wasDefault) {
            addresses.findFirstByUserIdOrderByCreatedAtAsc(userId)?.let {
                it.isDefault = true
                it.updatedAt = Instant.now()
                addresses.save(it)
            }
        }
    }

    @Transactional
    fun setDefault(userId: UUID, id: UUID) {
        val address = findOwned(userId, id)
        if (address.isDefault) return
        val now = Instant.now()
        addresses.findFirstByUserIdAndIsDefaultTrue(userId)?.let {
            it.isDefault = false
            it.updatedAt = now
            addresses.saveAndFlush(it)
        }
        address.isDefault = true
        address.updatedAt = now
        addresses.save(address)
    }

    private fun validateLocation(provinceCode: String, wardCode: String) {
        if (!locations.isValid(provinceCode, wardCode)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid_location",
                "Ward does not belong to the selected province.",
            )
        }
    }

    private fun findOwned(userId: UUID, id: UUID): UserAddress =
        addresses.findByIdAndUserId(id, userId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "address_not_found", "Address was not found.")

    private fun UserAddress.toResponse() = AddressResponse(
        id, label, contactName, contactPhone, addressLine, provinceCode, wardCode,
        isDefault, createdAt, updatedAt,
    )
}
