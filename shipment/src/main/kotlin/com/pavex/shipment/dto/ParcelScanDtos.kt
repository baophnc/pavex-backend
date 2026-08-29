package com.pavex.shipment.dto

import com.pavex.shipment.entity.ParcelStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RecordParcelScanRequest(
    @field:NotBlank
    @field:Size(max = 40)
    val parcelCode: String,
    val hubId: UUID,
    val nextStatus: ParcelStatus? = null,
    @field:Size(max = 500)
    val description: String? = null,
    val occurredAt: Instant? = null,
)
