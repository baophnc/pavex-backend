package com.pavex.network.dto.coverage

import com.pavex.network.entity.CoverageTier
import com.pavex.network.entity.ServiceAreaCoverage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCoverageRequest(
    val serviceAreaId: UUID,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:Size(max = 20) val wardCode: String? = null,
    val tier: CoverageTier = CoverageTier.STANDARD,
)

data class UpdateCoverageRequest(
    val serviceAreaId: UUID,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:Size(max = 20) val wardCode: String? = null,
    val tier: CoverageTier = CoverageTier.STANDARD,
)

data class UpdateCoverageStatusRequest(val active: Boolean, @field:Size(max = 500) val reason: String? = null)

data class CoverageResponse(
    val id: UUID,
    val serviceAreaId: UUID,
    val provinceCode: String,
    val wardCode: String?,
    val tier: CoverageTier,
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(coverage: ServiceAreaCoverage) = CoverageResponse(
            coverage.id, coverage.serviceAreaId, coverage.provinceCode, coverage.wardCode,
            coverage.tier, coverage.isActive, coverage.createdAt, coverage.updatedAt,
        )
    }
}
