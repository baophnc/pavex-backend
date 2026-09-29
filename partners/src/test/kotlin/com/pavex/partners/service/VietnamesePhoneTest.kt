package com.pavex.partners.service

import com.pavex.common.contact.VietnamesePhone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VietnamesePhoneTest {
    @Test
    fun `common mobile formats normalize to the local 10-digit form`() {
        listOf(
            "0912345678",
            " 0912 345 678 ",
            "0912.345.678",
            "(091) 234-5678",
            "+84912345678",
            "+84 912 345 678",
            "84912345678",
        ).forEach { assertEquals("0912345678", VietnamesePhone.normalize(it), it) }
    }

    @Test
    fun `numbers the Shipment service rejects are refused`() {
        listOf(
            "028 3823 4567",
            "1900 1234",
            "091234567",
            "09123456789",
            "+8409123456789",
            "0912abc678",
            "",
        ).forEach { assertNull(VietnamesePhone.normalize(it), it) }
    }
}
