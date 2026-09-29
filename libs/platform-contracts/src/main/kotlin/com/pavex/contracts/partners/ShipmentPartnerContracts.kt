package com.pavex.contracts.partners

import java.util.UUID

data class MerchantOrderCapabilitySnapshot(
    val merchantId: UUID?,
    val merchantCode: String?,
    val userId: UUID,
    val status: String?,
    val canCreateOrders: Boolean,
    val defaultPickupAddressId: UUID?,
    val restrictions: List<String> = emptyList(),
)

/** The merchant a user belongs to, for services that bill or report per merchant. */
data class MerchantAccountSnapshot(
    val merchantId: UUID,
    val merchantCode: String,
    val businessName: String,
    val status: String,
)

/** A merchant's saved pickup point; Shipment builds the sender of a shipment from it. */
data class MerchantPickupAddressSnapshot(
    val id: UUID,
    val merchantId: UUID,
    val name: String,
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
)

data class PartnerLocationResolutionRequest(
    val provinceCode: String,
    val wardCode: String,
)

data class PartnerLocationSnapshot(
    val provinceCode: String,
    val provinceName: String,
    val wardCode: String,
    val wardName: String,
)
