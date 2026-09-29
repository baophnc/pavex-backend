package com.pavex.shipment.entity

import com.pavex.common.contact.VietnamesePhone
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class ShipmentAddressSnapshot(
    @Column(nullable = false, length = 120)
    var contactName: String = "",
    @Column(nullable = false, length = 20)
    var contactPhone: String = "",
    @Column(nullable = false, length = 500)
    var addressLine: String = "",
    @Column(nullable = false, length = 20)
    var provinceCode: String = "",
    @Column(nullable = false, length = 160)
    var provinceName: String = "",
    @Column(nullable = false, length = 20)
    var wardCode: String = "",
    @Column(nullable = false, length = 160)
    var wardName: String = "",
) {
    companion object {
        fun create(
            contactName: String,
            contactPhone: String,
            addressLine: String,
            provinceCode: String,
            provinceName: String,
            wardCode: String,
            wardName: String,
        ): ShipmentAddressSnapshot = ShipmentAddressSnapshot(
            contactName = requireText(contactName, 120, "contactName"),
            contactPhone = normalizeVietnamesePhone(contactPhone),
            addressLine = requireText(addressLine, 500, "addressLine"),
            provinceCode = requireText(provinceCode, 20, "provinceCode"),
            provinceName = requireText(provinceName, 160, "provinceName"),
            wardCode = requireText(wardCode, 20, "wardCode"),
            wardName = requireText(wardName, 160, "wardName"),
        )

        private fun normalizeVietnamesePhone(value: String): String =
            requireNotNull(VietnamesePhone.normalize(value)) { "contactPhone must be a valid Vietnamese phone number." }

        private fun requireText(value: String, maxLength: Int, field: String): String {
            val normalized = value.trim()
            require(normalized.isNotEmpty() && normalized.length <= maxLength) {
                "$field is required and must not exceed $maxLength characters."
            }
            return normalized
        }
    }
}
