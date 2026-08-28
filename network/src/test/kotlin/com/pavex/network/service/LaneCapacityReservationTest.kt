package com.pavex.network.service

import com.pavex.network.entity.LaneCapacityReservation
import com.pavex.network.entity.LaneCapacityReservationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LaneCapacityReservationTest {
    @Test
    fun `reservation can be consumed idempotently`() {
        val reservation = LaneCapacityReservation()

        reservation.consume()
        reservation.consume()

        assertEquals(LaneCapacityReservationStatus.CONSUMED, reservation.status)
    }

    @Test
    fun `released reservation cannot be consumed`() {
        val reservation = LaneCapacityReservation()
        reservation.release()

        assertThrows(IllegalStateException::class.java) {
            reservation.consume()
        }
    }

    @Test
    fun `consumed reservation cannot be released`() {
        val reservation = LaneCapacityReservation()
        reservation.consume()

        assertThrows(IllegalStateException::class.java) {
            reservation.release()
        }
    }
}
