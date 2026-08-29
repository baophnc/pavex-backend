package com.pavex.pricing.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "quote_requests", schema = "pricing")
class QuoteRequest(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "rate_plan_id", nullable = false)
    var ratePlanId: UUID = UUID.randomUUID(),
    @Column(name = "rate_code", nullable = false, length = 60)
    var rateCode: String = "",
    @Column(name = "rate_revision", nullable = false)
    var rateRevision: Int = 1,
    @Column(name = "rate_version", nullable = false, length = 80)
    var rateVersion: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_zone", nullable = false, length = 30)
    var pricingZone: PricingZone = PricingZone.SAME_SERVICE_AREA,
    @Column(name = "origin_province_code", nullable = false, length = 20)
    var originProvinceCode: String = "",
    @Column(name = "origin_ward_code", nullable = false, length = 20)
    var originWardCode: String = "",
    @Column(name = "origin_service_area_id", nullable = false)
    var originServiceAreaId: UUID = UUID.randomUUID(),
    @Column(name = "origin_region_id", nullable = false)
    var originRegionId: UUID = UUID.randomUUID(),
    @Column(name = "origin_primary_hub_id", nullable = false)
    var originPrimaryHubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_coverage_tier", nullable = false, length = 20)
    var originCoverageTier: CoverageTier = CoverageTier.STANDARD,
    @Column(name = "destination_province_code", nullable = false, length = 20)
    var destinationProvinceCode: String = "",
    @Column(name = "destination_ward_code", nullable = false, length = 20)
    var destinationWardCode: String = "",
    @Column(name = "destination_service_area_id", nullable = false)
    var destinationServiceAreaId: UUID = UUID.randomUUID(),
    @Column(name = "destination_region_id", nullable = false)
    var destinationRegionId: UUID = UUID.randomUUID(),
    @Column(name = "destination_primary_hub_id", nullable = false)
    var destinationPrimaryHubId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "destination_coverage_tier", nullable = false, length = 20)
    var destinationCoverageTier: CoverageTier = CoverageTier.STANDARD,
    @Column(name = "actual_weight_kg", nullable = false, precision = 12, scale = 2)
    var actualWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "volumetric_weight_kg", nullable = false, precision = 12, scale = 2)
    var volumetricWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "chargeable_weight_kg", nullable = false, precision = 12, scale = 2)
    var chargeableWeightKg: BigDecimal = BigDecimal.ZERO,
    @Column(name = "parcel_count", nullable = false)
    var parcelCount: Int = 0,
    @Column(name = "cod_amount", nullable = false)
    var codAmount: Long = 0,
    @Column(name = "declared_value", nullable = false)
    var declaredValue: Long = 0,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),
) {
    @OneToMany(mappedBy = "quoteRequest", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val parcels: MutableList<QuoteParcel> = mutableListOf()

    @OneToMany(mappedBy = "quoteRequest", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    val options: MutableList<QuoteOption> = mutableListOf()
}
