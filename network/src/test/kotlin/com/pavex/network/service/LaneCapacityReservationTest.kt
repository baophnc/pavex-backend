package com.pavex.network.service

import com.pavex.network.entity.LaneCapacityReservation
import com.pavex.network.entity.LaneCapacityReservationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

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

    @Test
    fun `unconfirmed shipment reservation expires after its deadline`() {
        val now = Instant.parse("2026-09-20T10:00:00Z")
        val reservation = LaneCapacityReservation(confirmationDeadlineAt = now.minusSeconds(1))

        assertTrue(reservation.isExpiredUnconfirmed(now))
    }

    @Test
    fun `reservation is not expired before its deadline`() {
        val now = Instant.parse("2026-09-20T10:00:00Z")
        val reservation = LaneCapacityReservation(confirmationDeadlineAt = now.plusSeconds(60))

        assertFalse(reservation.isExpiredUnconfirmed(now))
    }

    @Test
    fun `confirmed, consumed or admin reservations never expire`() {
        val now = Instant.parse("2026-09-20T10:00:00Z")
        val past = now.minusSeconds(3600)

        val confirmed = LaneCapacityReservation(confirmationDeadlineAt = past).apply { confirm(now) }
        val consumed = LaneCapacityReservation(confirmationDeadlineAt = past).apply { consume() }
        val admin = LaneCapacityReservation(confirmationDeadlineAt = null)

        assertFalse(confirmed.isExpiredUnconfirmed(now))
        assertFalse(consumed.isExpiredUnconfirmed(now))
        assertFalse(admin.isExpiredUnconfirmed(now))
    }

    @Test
    fun `confirm is idempotent and keeps the first confirmation time`() {
        val first = Instant.parse("2026-09-20T10:00:00Z")
        val reservation = LaneCapacityReservation(confirmationDeadlineAt = first.plusSeconds(60))

        reservation.confirm(first)
        reservation.confirm(first.plusSeconds(30))

        assertEquals(first, reservation.confirmedAt)
    }
}
