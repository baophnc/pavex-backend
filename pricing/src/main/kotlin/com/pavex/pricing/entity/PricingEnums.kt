package com.pavex.pricing.entity

enum class RatePlanStatus { DRAFT, ACTIVE, RETIRED }
enum class ServiceLevel { STANDARD, EXPRESS }
/** Nội tỉnh, nội miền, liên miền: how far apart the sender's and recipient's provinces are. */
enum class PricingZone { SAME_PROVINCE, SAME_REGION, CROSS_REGION }
enum class CoverageTier { STANDARD, REMOTE, ISLAND }
