package com.pavex.network.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

enum class HubType { REGIONAL_GATEWAY, PROVINCIAL, LOCAL }
enum class HubStatus { INACTIVE, ACTIVE }

@Entity
@Table(name = "hubs", schema = "network")
class Hub(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 50)
    var code: String = "",
    @Column(nullable = false, length = 200)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: HubType = HubType.LOCAL,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: HubStatus = HubStatus.ACTIVE,
    @Column(name = "address_line", nullable = false, length = 500)
    var addressLine: String = "",
    @Column(name = "province_code", nullable = false, length = 20)
    var provinceCode: String = "",
    @Column(name = "ward_code", nullable = false, length = 20)
    var wardCode: String = "",
    @Column(name = "parent_hub_id")
    var parentHubId: UUID? = null,
    @Column(name = "public_visible", nullable = false)
    var publicVisible: Boolean = false,
    @Column(name = "public_name", length = 200)
    var publicName: String? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Version
    var version: Long = 0,
) {
    fun update(
        name: String,
        type: HubType,
        addressLine: String,
        provinceCode: String,
        wardCode: String,
        parentHubId: UUID?,
        publicVisible: Boolean,
        publicName: String?,
        latitude: Double? = this.latitude,
        longitude: Double? = this.longitude,
    ) {
        this.name = name.trim()
        this.type = type
        this.addressLine = addressLine.trim()
        this.provinceCode = provinceCode.trim()
        this.wardCode = wardCode.trim()
        this.parentHubId = parentHubId
        this.publicVisible = publicVisible
        this.publicName = publicName?.trim()?.takeIf { it.isNotBlank() }
        this.latitude = latitude
        this.longitude = longitude
        this.updatedAt = Instant.now()
    }

    fun setActive(active: Boolean) {
        status = if (active) HubStatus.ACTIVE else HubStatus.INACTIVE
        updatedAt = Instant.now()
    }
}
