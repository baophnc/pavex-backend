package com.pavex.shipment.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A photo or signature a courier captured as proof of delivery. */
@Entity
@Table(name = "delivery_evidence", schema = "shipments")
class DeliveryEvidence(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "uploaded_by_user_id", nullable = false)
    var uploadedByUserId: UUID = UUID.randomUUID(),
    @Column(name = "content_type", nullable = false, length = 50)
    var contentType: String = "",
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Int = 0,
    @Column(nullable = false, length = 64)
    var sha256: String = "",
    @Column(nullable = false)
    var data: ByteArray = ByteArray(0),
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
