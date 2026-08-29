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
