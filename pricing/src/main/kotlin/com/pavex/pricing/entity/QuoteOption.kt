package com.pavex.pricing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "quote_options", schema = "pricing")
class QuoteOption(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_request_id", nullable = false)
    var quoteRequest: QuoteRequest = QuoteRequest(),
    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false, length = 20)
    var serviceLevel: ServiceLevel = ServiceLevel.STANDARD,
    @Column(name = "base_fee", nullable = false)
    var baseFee: Long = 0,
    @Column(name = "additional_weight_fee", nullable = false)
    var additionalWeightFee: Long = 0,
    @Column(name = "area_surcharge", nullable = false)
    var areaSurcharge: Long = 0,
    @Column(name = "cod_fee", nullable = false)
    var codFee: Long = 0,
    @Column(name = "insurance_fee", nullable = false)
    var insuranceFee: Long = 0,
    @Column(nullable = false)
    var total: Long = 0,
    @Column(name = "estimated_min_days", nullable = false)
    var estimatedMinDays: Int = 0,
    @Column(name = "estimated_max_days", nullable = false)
    var estimatedMaxDays: Int = 0,
)
