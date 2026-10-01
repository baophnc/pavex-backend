package com.pavex.shipment.service

import com.pavex.shipment.entity.DeliveryFailureReason
import com.pavex.shipment.entity.ParcelStatus

/** Presentation only: persisted enum/API codes are deliberately unchanged. */
object ShipmentVietnameseText {
    fun parcelStatus(status: ParcelStatus): String = when (status) {
        ParcelStatus.AWAITING_PICKUP -> "Chờ lấy hàng"
        ParcelStatus.PICKED_UP -> "Đã lấy hàng"
        ParcelStatus.AT_ORIGIN_HUB -> "Đã đến bưu cục gửi"
        ParcelStatus.IN_TRANSIT -> "Đang trung chuyển"
        ParcelStatus.AT_TRANSIT_HUB -> "Đã đến bưu cục trung chuyển"
        ParcelStatus.AT_DESTINATION_HUB -> "Đã đến bưu cục phát"
        ParcelStatus.OUT_FOR_DELIVERY -> "Đang giao hàng"
        ParcelStatus.DELIVERY_FAILED -> "Giao hàng không thành công"
        ParcelStatus.DELIVERED -> "Đã giao hàng"
        ParcelStatus.RETURNING -> "Đang hoàn hàng"
        ParcelStatus.RETURNED -> "Đã hoàn hàng"
        ParcelStatus.CANCELLED -> "Đã hủy"
    }

    fun deliveryFailure(reason: DeliveryFailureReason?): String = when (reason) {
        DeliveryFailureReason.RECIPIENT_UNAVAILABLE -> "Người nhận vắng mặt"
        DeliveryFailureReason.RECIPIENT_REFUSED -> "Người nhận từ chối nhận hàng"
        DeliveryFailureReason.INVALID_ADDRESS -> "Địa chỉ không hợp lệ"
        DeliveryFailureReason.UNABLE_TO_CONTACT -> "Không liên hệ được người nhận"
        DeliveryFailureReason.PAYMENT_NOT_COLLECTED -> "Chưa thu được tiền"
        DeliveryFailureReason.PARCEL_DAMAGED -> "Kiện hàng bị hư hỏng"
        DeliveryFailureReason.UNSAFE_LOCATION -> "Địa điểm giao hàng không an toàn"
        DeliveryFailureReason.OTHER, null -> "Lý do khác"
    }

    fun scan(status: ParcelStatus?, hubName: String): String =
        if (status == null) "Đã quét kiện hàng tại $hubName."
        else "${parcelStatus(status)} tại $hubName."

    /** Translate only recognized legacy system templates, preserving custom notes. */
    fun event(description: String): String {
        when (description) {
            "Shipment created and awaiting pickup." -> return "Đã tạo đơn hàng, chờ nhân viên lấy hàng."
            "Shipment lifecycle completed." -> return "Đã hoàn tất vòng đời đơn hàng."
            "Parcel delivered with proof." -> return "Đã giao hàng và ghi nhận bằng chứng giao nhận."
            // Case events written before they were recorded in Vietnamese.
            "PROBLEM_PARCEL case created." -> return "Tạo hồ sơ kiện có vấn đề."
            "UNIDENTIFIED_PARCEL case created." -> return "Tạo hồ sơ kiện chưa xác định."
            "Case assigned." -> return "Đã phân công người xử lý."
            "Investigation started." -> return "Bắt đầu điều tra."
            "Unidentified parcel linked to shipment." -> return "Đã xác định vận đơn cho kiện."
            "Case closed." -> return "Đã đóng hồ sơ."
        }
        legacyScan.matchEntire(description)?.let { match ->
            val status = ParcelStatus.entries.find { it.name == match.groupValues[1] }
                ?: return description
            return scan(status, match.groupValues[2])
        }
        legacyCheckpoint.matchEntire(description)?.let { return scan(null, it.groupValues[1]) }
        legacyFailure.matchEntire(description)?.let { match ->
            val reason = DeliveryFailureReason.entries.find { it.name == match.groupValues[1] }
                ?: return description
            return "Giao hàng không thành công: ${deliveryFailure(reason)}."
        }
        return description
    }

    private val legacyScan = Regex("Parcel status updated to ([A-Z_]+) at (.+)\\.")
    private val legacyCheckpoint = Regex("Parcel scanned at (.+)\\.")
    private val legacyFailure = Regex("Delivery attempt failed: ([A-Z_]+)\\.")
}
