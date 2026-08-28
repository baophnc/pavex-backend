package com.pavex.partners.dto

import com.pavex.partners.entity.MerchantStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class PickupAddressRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:NotBlank @field:Size(max = 200)
    val contactName: String,
    @field:NotBlank @field:Size(max = 30)
    val contactPhone: String,
    @field:NotBlank @field:Size(max = 500)
    val addressLine: String,
    @field:NotBlank @field:Size(max = 20)
    val provinceCode: String,
    @field:NotBlank @field:Size(max = 20)
    val wardCode: String,
    val isDefault: Boolean = false,
)

data class PickupAddressResponse(
    val id: UUID,
    val name: String,
    val contactName: String,
    val contactPhone: String,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
    val isDefault: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class OrderCapabilityResponse(
    val merchantId: UUID?,
    val merchantCode: String?,
    val userId: UUID,
    val status: MerchantStatus?,
    val canCreateOrder: Boolean,
    val defaultPickupAddressId: UUID?,
    val restrictions: List<String>,
)
