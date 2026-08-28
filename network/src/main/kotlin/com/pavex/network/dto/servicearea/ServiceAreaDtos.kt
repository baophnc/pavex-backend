package com.pavex.network.dto.servicearea

import com.pavex.network.entity.ServiceArea
import com.pavex.network.entity.ServiceAreaStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateServiceAreaRequest(
    @field:NotBlank @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Z0-9_-]+$")
    val code: String,
    @field:NotBlank @field:Size(max = 200) val name: String,
    val regionId: UUID,
    val primaryHubId: UUID,
)

data class UpdateServiceAreaRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    val regionId: UUID,
    val primaryHubId: UUID,
)

data class UpdateServiceAreaStatusRequest(val active: Boolean, @field:Size(max = 500) val reason: String? = null)

data class ServiceAreaResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val regionId: UUID,
    val primaryHubId: UUID,
    val status: ServiceAreaStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(area: ServiceArea) = ServiceAreaResponse(
            area.id, area.code, area.name, area.regionId, area.primaryHubId,
            area.status, area.createdAt, area.updatedAt,
        )
    }
}
