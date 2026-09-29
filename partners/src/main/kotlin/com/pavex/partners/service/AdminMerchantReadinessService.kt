package com.pavex.partners.service

import com.pavex.common.contact.VietnamesePhone
import com.pavex.partners.dto.AdminMerchantReadinessResponse
import com.pavex.partners.dto.AdminPickupAddressResponse
import com.pavex.partners.exception.ApiException
import com.pavex.partners.exception.IdentityServiceUnavailableException
import com.pavex.partners.repository.MerchantPickupAddressRepository
import com.pavex.partners.repository.MerchantRepository
import com.pavex.partners.repository.ProvinceRepository
import com.pavex.partners.repository.WardRepository
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Lets an admin see what a merchant sees when creating an order: the same restrictions the
 * Shipment service enforces, plus which pickup addresses it would accept as a sender.
 */
@Service
class AdminMerchantReadinessService(
    private val merchants: MerchantRepository,
    private val merchantService: MerchantService,
    private val pickupAddresses: MerchantPickupAddressRepository,
    private val identityClient: IdentityMerchantGateway,
    private val provinces: ProvinceRepository,
    private val wards: WardRepository,
) {
    @Transactional(readOnly = true)
    fun readiness(merchantId: UUID): AdminMerchantReadinessResponse {
        val merchant = merchants.findById(merchantId).orElseThrow {
            ApiException(HttpStatus.NOT_FOUND, "merchant_not_found", "Merchant was not found.")
        }
        // The review screen stays usable while Identity is down; the identity check shows as unknown.
        val identityEligible = try {
            identityClient.findMerchantEligibility(merchant.userId)?.eligible == true
        } catch (_: IdentityServiceUnavailableException) {
            null
        }
        val capability = merchantService.capabilityOf(merchant, identityEligible)

        val addresses = pickupAddresses.findAllByMerchantIdOrderByIsDefaultDescCreatedAtAsc(merchant.id)
        val provinceNames = provinces.findAllById(addresses.map { it.provinceCode }.toSet())
            .associate { it.code to it.fullName.ifBlank { it.name } }
        val wardsByCode = wards.findAllById(addresses.map { it.wardCode }.toSet()).associateBy { it.code }

        return AdminMerchantReadinessResponse(
            canCreateOrder = if (identityEligible == null && capability.canCreateOrder) null else capability.canCreateOrder,
            restrictions = capability.restrictions,
            identityChecked = identityEligible != null,
            defaultPickupAddressId = capability.defaultPickupAddressId,
            pickupAddresses = addresses.map { address ->
                val ward = wardsByCode[address.wardCode]?.takeIf { it.provinceCode == address.provinceCode }
                AdminPickupAddressResponse(
                    id = address.id,
                    name = address.name,
                    contactName = address.contactName,
                    contactPhone = address.contactPhone,
                    addressLine = address.addressLine,
                    provinceCode = address.provinceCode,
                    provinceName = provinceNames[address.provinceCode],
                    wardCode = address.wardCode,
                    wardName = ward?.let { it.fullName.ifBlank { it.name } },
                    isDefault = address.isDefault,
                    usableAsSender = ward != null &&
                        address.contactName.isNotBlank() &&
                        address.addressLine.isNotBlank() &&
                        VietnamesePhone.normalize(address.contactPhone) != null,
                    createdAt = address.createdAt,
                    updatedAt = address.updatedAt,
                )
            },
        )
    }
}
