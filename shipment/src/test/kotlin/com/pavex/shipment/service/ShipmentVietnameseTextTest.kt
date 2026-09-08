package com.pavex.shipment.service

import com.pavex.shipment.entity.ParcelStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ShipmentVietnameseTextTest {
    @Test
    fun `all parcel statuses have Vietnamese labels without changing codes`() {
        ParcelStatus.entries.forEach {
            assertNotEquals(it.name, ShipmentVietnameseText.parcelStatus(it))
            assertFalse(ShipmentVietnameseText.scan(it, "Bưu cục Hoàn Kiếm").contains(it.name))
        }
    }

    @Test
    fun `legacy system timeline is translated but custom notes remain intact`() {
        assertEquals("Đã tạo đơn hàng, chờ nhân viên lấy hàng.",
            ShipmentVietnameseText.event("Shipment created and awaiting pickup."))
        assertEquals("Đã đến bưu cục trung chuyển tại Tổng kho Hà Nội.",
            ShipmentVietnameseText.event("Parcel status updated to AT_TRANSIT_HUB at Tổng kho Hà Nội."))
        assertEquals("Giao hàng không thành công: Người nhận vắng mặt.",
            ShipmentVietnameseText.event("Delivery attempt failed: RECIPIENT_UNAVAILABLE."))
        listOf("Khách hẹn giao lại vào buổi chiều.", "Custom English note", "Parcel status updated to FUTURE at Hub.").forEach {
            assertEquals(it, ShipmentVietnameseText.event(it))
        }
    }
}
