package com.pavex.pricing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "quote_parcels", schema = "pricing")
class QuoteParcel(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_request_id", nullable = false)
    var quoteRequest: QuoteRequest = QuoteRequest(),
    @Column(nullable = false)
    var sequence: Int = 1,
    @Column(name = "actual_weight_kg", nullable = false, precision = 10, scale = 2)
    var actualWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "volumetric_weight_kg", nullable = false, precision = 10, scale = 2)
    var volumetricWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "chargeable_weight_kg", nullable = false, precision = 10, scale = 2)
    var chargeableWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "length_cm", nullable = false, precision = 10, scale = 2)
    var lengthCm: BigDecimal = BigDecimal.ZERO,
    @Column(name = "width_cm", nullable = false, precision = 10, scale = 2)
    var widthCm: BigDecimal = BigDecimal.ZERO,
    @Column(name = "height_cm", nullable = false, precision = 10, scale = 2)
    var heightCm: BigDecimal = BigDecimal.ZERO,
)
