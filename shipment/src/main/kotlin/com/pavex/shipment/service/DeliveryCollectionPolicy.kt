package com.pavex.shipment.service

import com.pavex.shipment.entity.DeliveryAttemptOutcome
import com.pavex.shipment.entity.Shipment
import com.pavex.shipment.entity.ShipmentCollectionStatus
import com.pavex.shipment.exception.InvalidShipmentRequestException

/**
 * Money is collected once per shipment, in full, on the first parcel delivered successfully.
 * The courier reports the amount so a wrong or missing collection is caught at the doorstep.
 */
object DeliveryCollectionPolicy {
    /** Returns the amount to record on this attempt, or null when nothing is collected. */
    fun resolve(shipment: Shipment, outcome: DeliveryAttemptOutcome, collectedAmount: Long?): Long? {
        val expected = outcome == DeliveryAttemptOutcome.DELIVERED &&
            shipment.collectionStatus() == ShipmentCollectionStatus.PENDING
        if (expected) {
            if (collectedAmount != shipment.recipientCollectAmount) {
                throw InvalidShipmentRequestException(
                    "collection_amount_mismatch",
                    "Collect exactly ${shipment.recipientCollectAmount} VND from the recipient. " +
                        "If the recipient refuses to pay, record a failed delivery instead.",
                )
            }
            return collectedAmount
        }
        if (collectedAmount != null && collectedAmount != 0L) {
            throw InvalidShipmentRequestException(
                "collection_not_expected",
                "No money is collected on this delivery attempt.",
            )
        }
        return null
    }
}
