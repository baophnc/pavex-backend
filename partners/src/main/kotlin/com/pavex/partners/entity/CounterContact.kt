package com.pavex.partners.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A walk-in customer the counter served: one phone, one address, reused on their next visit. */
@Entity
@Table(name = "counter_contacts", schema = "partners")
class CounterContact(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 20)
    var phone: String = "",
    @Column(nullable = false, length = 120)
    var name: String = "",
    @Column(name = "address_line", nullable = false, length = 500)
    var addressLine: String = "",
    @Column(name = "province_code", nullable = false, length = 20)
    var provinceCode: String = "",
    @Column(name = "ward_code", nullable = false, length = 20)
    var wardCode: String = "",
    @Column(name = "use_count", nullable = false)
    var useCount: Int = 1,
    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: Instant = Instant.EPOCH,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,
)
