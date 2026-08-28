package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_pickup_addresses", schema = "partners")
class MerchantPickupAddress(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 100)
    var name: String = "",
    @Column(name = "contact_name", nullable = false, length = 200)
    var contactName: String = "",
    @Column(name = "contact_phone", nullable = false, length = 30)
    var contactPhone: String = "",
    @Column(name = "address_line", nullable = false, length = 500)
    var addressLine: String = "",
    @Column(name = "province_code", nullable = false, length = 20)
    var provinceCode: String = "",
    @Column(name = "ward_code", nullable = false, length = 20)
    var wardCode: String = "",
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
