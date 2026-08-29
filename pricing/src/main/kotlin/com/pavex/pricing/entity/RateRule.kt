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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "rate_rules", schema = "pricing")
class RateRule(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rate_plan_id", nullable = false)
    var ratePlan: RatePlan = RatePlan(),
    @Enumerated(EnumType.STRING)
    @Column(name = "service_level", nullable = false, length = 20)
    var serviceLevel: ServiceLevel = ServiceLevel.STANDARD,
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_zone", nullable = false, length = 30)
    var pricingZone: PricingZone = PricingZone.SAME_SERVICE_AREA,
    @Column(name = "base_fee", nullable = false)
    var baseFee: Long = 0,
    @Column(name = "included_weight_kg", nullable = false, precision = 10, scale = 2)
    var includedWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "additional_weight_step_kg", nullable = false, precision = 10, scale = 2)
    var additionalWeightStepKg: BigDecimal = BigDecimal("0.50"),
    @Column(name = "additional_weight_step_fee", nullable = false)
    var additionalWeightStepFee: Long = 0,
    @Column(name = "estimated_min_days", nullable = false)
    var estimatedMinDays: Int = 1,
    @Column(name = "estimated_max_days", nullable = false)
    var estimatedMaxDays: Int = 1,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    fun update(
        baseFee: Long,
        includedWeightKg: BigDecimal,
        additionalWeightStepKg: BigDecimal,
        additionalWeightStepFee: Long,
        estimatedMinDays: Int,
        estimatedMaxDays: Int,
    ) {
        this.baseFee = baseFee
        this.includedWeightKg = includedWeightKg
        this.additionalWeightStepKg = additionalWeightStepKg
        this.additionalWeightStepFee = additionalWeightStepFee
        this.estimatedMinDays = estimatedMinDays
        this.estimatedMaxDays = estimatedMaxDays
        this.updatedAt = Instant.now()
    }
}
