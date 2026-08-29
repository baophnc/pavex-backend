package com.pavex.pricing.entity

import com.pavex.pricing.exception.ConflictException
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "rate_plans",
    schema = "pricing",
    uniqueConstraints = [UniqueConstraint(name = "ux_pricing_rate_plan_code_revision", columnNames = ["code", "revision"])],
)
class RatePlan(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 60)
    var code: String = "",
    @Column(nullable = false)
    var revision: Int = 1,
    @Column(nullable = false, length = 200)
    var name: String = "",
    @Column(nullable = false, length = 3)
    var currency: String = "VND",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RatePlanStatus = RatePlanStatus.DRAFT,
    @Column(name = "effective_from")
    var effectiveFrom: Instant? = null,
    @Column(name = "effective_to")
    var effectiveTo: Instant? = null,
    @Column(name = "quote_ttl_minutes", nullable = false)
    var quoteTtlMinutes: Int = 30,
    @Column(name = "volumetric_divisor", nullable = false, precision = 12, scale = 2)
    var volumetricDivisor: BigDecimal = BigDecimal("5000.00"),
    @Column(name = "remote_endpoint_surcharge", nullable = false)
    var remoteEndpointSurcharge: Long = 0,
    @Column(name = "island_transport_rate", nullable = false, precision = 10, scale = 6)
    var islandTransportRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "cod_free_threshold", nullable = false)
    var codFreeThreshold: Long = 0,
    @Column(name = "cod_rate", nullable = false, precision = 10, scale = 6)
    var codRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "cod_minimum_fee", nullable = false)
    var codMinimumFee: Long = 0,
    @Column(name = "maximum_cod", nullable = false)
    var maximumCod: Long = 0,
    @Column(name = "insurance_free_threshold", nullable = false)
    var insuranceFreeThreshold: Long = 0,
    @Column(name = "insurance_rate", nullable = false, precision = 10, scale = 6)
    var insuranceRate: BigDecimal = BigDecimal.ZERO,
    @Column(name = "maximum_declared_value", nullable = false)
    var maximumDeclaredValue: Long = 0,
    @Column(name = "max_parcel_weight_kg", nullable = false, precision = 10, scale = 2)
    var maxParcelWeightKg: BigDecimal = BigDecimal("30.00"),
    @Column(name = "max_shipment_chargeable_weight_kg", nullable = false, precision = 10, scale = 2)
    var maxShipmentChargeableWeightKg: BigDecimal = BigDecimal("30.00"),
    @Column(name = "max_dimension_cm", nullable = false, precision = 10, scale = 2)
    var maxDimensionCm: BigDecimal = BigDecimal("200.00"),
    @Column(name = "created_by")
    var createdBy: UUID? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "activated_by")
    var activatedBy: UUID? = null,
    @Column(name = "activated_at")
    var activatedAt: Instant? = null,
    @Column(name = "retired_by")
    var retiredBy: UUID? = null,
    @Column(name = "retired_at")
    var retiredAt: Instant? = null,
    @Version
    var version: Long = 0,
) {
    @OneToMany(mappedBy = "ratePlan", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val rules: MutableList<RateRule> = mutableListOf()

    fun updateDraft(
        name: String,
        effectiveFrom: Instant?,
        effectiveTo: Instant?,
        quoteTtlMinutes: Int,
        volumetricDivisor: BigDecimal,
        remoteEndpointSurcharge: Long,
        islandTransportRate: BigDecimal,
        codFreeThreshold: Long,
        codRate: BigDecimal,
        codMinimumFee: Long,
        maximumCod: Long,
        insuranceFreeThreshold: Long,
        insuranceRate: BigDecimal,
        maximumDeclaredValue: Long,
        maxParcelWeightKg: BigDecimal,
        maxShipmentChargeableWeightKg: BigDecimal,
        maxDimensionCm: BigDecimal,
    ) {
        requireDraft()
        this.name = name.trim()
        this.effectiveFrom = effectiveFrom
        this.effectiveTo = effectiveTo
        this.quoteTtlMinutes = quoteTtlMinutes
        this.volumetricDivisor = volumetricDivisor
        this.remoteEndpointSurcharge = remoteEndpointSurcharge
        this.islandTransportRate = islandTransportRate
        this.codFreeThreshold = codFreeThreshold
        this.codRate = codRate
        this.codMinimumFee = codMinimumFee
        this.maximumCod = maximumCod
        this.insuranceFreeThreshold = insuranceFreeThreshold
        this.insuranceRate = insuranceRate
        this.maximumDeclaredValue = maximumDeclaredValue
        this.maxParcelWeightKg = maxParcelWeightKg
        this.maxShipmentChargeableWeightKg = maxShipmentChargeableWeightKg
        this.maxDimensionCm = maxDimensionCm
        updatedAt = Instant.now()
    }

    fun upsertRule(
        serviceLevel: ServiceLevel,
        pricingZone: PricingZone,
        baseFee: Long,
        includedWeightKg: BigDecimal,
        additionalWeightStepKg: BigDecimal,
        additionalWeightStepFee: Long,
        estimatedMinDays: Int,
        estimatedMaxDays: Int,
    ): RateRule {
        requireDraft()
        val existing = rules.firstOrNull {
            it.serviceLevel == serviceLevel && it.pricingZone == pricingZone
        }
        if (existing != null) {
            existing.update(
                baseFee,
                includedWeightKg,
                additionalWeightStepKg,
                additionalWeightStepFee,
                estimatedMinDays,
                estimatedMaxDays,
            )
            updatedAt = Instant.now()
            return existing
        }

        val rule = RateRule(
            ratePlan = this,
            serviceLevel = serviceLevel,
            pricingZone = pricingZone,
            baseFee = baseFee,
            includedWeightKg = includedWeightKg,
            additionalWeightStepKg = additionalWeightStepKg,
            additionalWeightStepFee = additionalWeightStepFee,
            estimatedMinDays = estimatedMinDays,
            estimatedMaxDays = estimatedMaxDays,
        )
        rules += rule
        updatedAt = Instant.now()
        return rule
    }

    fun removeRule(serviceLevel: ServiceLevel, pricingZone: PricingZone): Boolean {
        requireDraft()
        val removed = rules.removeIf {
            it.serviceLevel == serviceLevel && it.pricingZone == pricingZone
        }
        if (removed) updatedAt = Instant.now()
        return removed
    }

    fun requireDraft() {
        if (status != RatePlanStatus.DRAFT) {
            throw ConflictException(
                "rate_plan_immutable",
                "Only draft rate plans can be edited. Create a new revision instead of changing an active or retired plan.",
            )
        }
    }
}
