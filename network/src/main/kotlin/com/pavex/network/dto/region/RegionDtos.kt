package com.pavex.network.dto.region

import com.pavex.network.entity.NetworkRegion
import com.pavex.network.entity.NetworkRegionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateRegionRequest(
    @field:NotBlank @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Z0-9_]+$")
    val code: String,
    @field:NotBlank @field:Size(max = 160)
    val name: String,
)

data class UpdateRegionRequest(
    @field:NotBlank @field:Size(max = 160)
    val name: String,
)

data class UpdateRegionStatusRequest(val active: Boolean, @field:Size(max = 500) val reason: String? = null)

data class RegionResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val status: NetworkRegionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(region: NetworkRegion) = RegionResponse(
            region.id, region.code, region.name, region.status, region.createdAt, region.updatedAt,
        )
    }
}
