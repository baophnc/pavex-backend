package com.pavex.partners.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** A sender or recipient the counter saves after booking, keyed by phone. */
data class SaveCounterContactRequest(
    @field:NotBlank @field:Size(max = 20) val phone: String,
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 500) val addressLine: String,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:NotBlank @field:Size(max = 20) val wardCode: String,
)

data class CounterContactResponse(
    val id: UUID,
    val phone: String,
    val name: String,
    val addressLine: String,
    val provinceCode: String,
    val provinceName: String?,
    val wardCode: String,
    val wardName: String?,
    val useCount: Int,
    val lastUsedAt: Instant,
)
