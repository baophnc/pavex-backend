package com.pavex.network.dto.hub

import com.pavex.network.entity.Hub
import com.pavex.network.entity.HubStatus
import com.pavex.network.entity.HubType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateHubRequest(
    @field:NotBlank @field:Size(max = 50)
    @field:Pattern(regexp = "^[A-Z0-9_-]+$")
    val code: String,
    @field:NotBlank @field:Size(max = 200) val name: String,
    val type: HubType,
    @field:NotBlank @field:Size(max = 500) val addressLine: String,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:NotBlank @field:Size(max = 20) val wardCode: String,
    val parentHubId: UUID? = null,
    val publicVisible: Boolean = false,
    @field:Size(max = 200) val publicName: String? = null,
)

data class UpdateHubRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    val type: HubType,
    @field:NotBlank @field:Size(max = 500) val addressLine: String,
    @field:NotBlank @field:Size(max = 20) val provinceCode: String,
    @field:NotBlank @field:Size(max = 20) val wardCode: String,
    val parentHubId: UUID? = null,
    val publicVisible: Boolean = false,
    @field:Size(max = 200) val publicName: String? = null,
)

data class UpdateHubStatusRequest(val active: Boolean, @field:Size(max = 500) val reason: String? = null)

data class HubResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val type: HubType,
    val status: HubStatus,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
    val parentHubId: UUID?,
    val publicVisible: Boolean,
    val publicName: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(hub: Hub) = HubResponse(
            hub.id, hub.code, hub.name, hub.type, hub.status, hub.addressLine,
            hub.provinceCode, hub.wardCode, hub.parentHubId, hub.publicVisible,
            hub.publicName, hub.createdAt, hub.updatedAt,
        )
    }
}

data class PublicHubResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val type: HubType,
    val addressLine: String,
    val provinceCode: String,
    val wardCode: String,
) {
    companion object {
        fun from(hub: Hub) = PublicHubResponse(
            hub.id,
            hub.code,
            hub.publicName ?: hub.name,
            hub.type,
            hub.addressLine,
            hub.provinceCode,
            hub.wardCode,
        )
    }
}
