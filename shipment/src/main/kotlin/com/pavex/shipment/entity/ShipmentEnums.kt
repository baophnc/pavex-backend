package com.pavex.shipment.entity

enum class ShipmentStatus {
    AWAITING_PICKUP,
    PICKED_UP,
    AT_ORIGIN_HUB,
    IN_TRANSIT,
    AT_DESTINATION_HUB,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    PARTIALLY_DELIVERED,
    DELIVERED,
    RETURNING,
    RETURNED,
    CANCELLED,
}

enum class ShipmentLifecycleStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
}

enum class ShippingFeePayer { SENDER, RECIPIENT }

enum class ShipmentRoutingStatus { UNPLANNED, RESERVED, IN_TRANSIT, CONSUMED, RELEASED }

enum class ShipmentServiceLevel { STANDARD, EXPRESS }

enum class ShipmentPricingZone { SAME_SERVICE_AREA, SAME_REGION, CROSS_REGION }

enum class ShipmentCoverageTier { STANDARD, REMOTE, ISLAND }

enum class GoodsCategory {
    DOCUMENTS,
    APPAREL,
    ELECTRONICS,
    DRY_FOOD,
    COSMETICS,
    HOUSEHOLD,
    FRAGILE_GOODS,
    OTHER,
}

enum class ParcelStatus {
    AWAITING_PICKUP,
    PICKED_UP,
    AT_ORIGIN_HUB,
    IN_TRANSIT,
    AT_DESTINATION_HUB,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    RETURNING,
    RETURNED,
    CANCELLED,
}

enum class ParcelStage {
    AWAITING_PICKUP,
    PICKED_UP,
    AT_HUB,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERY_EXCEPTION,
    DELIVERED,
    RETURNING,
    RETURNED,
    CANCELLED,
}

enum class ParcelFlowDirection { FORWARD, RETURN }

enum class ParcelLocationType { SENDER, HUB, ROUTE_LEG, COURIER, RECIPIENT, UNKNOWN }

enum class ParcelCustodyType { SENDER, HUB, LINE_HAUL, COURIER, RECIPIENT, UNKNOWN }

enum class ShipmentOutboxStatus { PENDING, PUBLISHED, FAILED }
