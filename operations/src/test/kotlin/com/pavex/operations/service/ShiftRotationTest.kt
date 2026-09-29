package com.pavex.operations.service

import com.pavex.operations.entity.WorkforceMemberKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class ShiftRotationTest {
    private val monday = LocalDate.parse("2026-09-28")

    @Test
    fun `hub size decides the shifts`() {
        assertEquals(listOf("Ca hành chính"), ShiftRotation.slots("LOCAL", 3).map { it.name })
        assertEquals(listOf("Ca sáng", "Ca chiều"), ShiftRotation.slots("LOCAL", 9).map { it.name })
        assertEquals(listOf("Ca sáng", "Ca chiều", "Ca đêm"), ShiftRotation.slots("PROVINCIAL", 20).map { it.name })
        assertTrue(ShiftRotation.slots("LOCAL", 0).isEmpty())
        assertEquals(listOf("Ca sáng", "Ca chiều"), ShiftRotation.slots("PROVINCIAL", 12, drivers = true).map { it.name })
    }

    @Test
    fun `everyone works six days and every shift is covered every day`() {
        for ((hubType, size) in listOf("LOCAL" to 2, "LOCAL" to 4, "LOCAL" to 5, "LOCAL" to 12, "PROVINCIAL" to 6, "REGIONAL_GATEWAY" to 70)) {
            val members = ShiftRotation.ordered(List(size) { UUID.randomUUID() })
            val slots = ShiftRotation.slots(hubType, size)
            val days = (0L until 14L).map(monday::plusDays)
            val plans = days.map { ShiftRotation.plan(members, slots.size, it) }

            for ((day, plan) in days.zip(plans)) {
                for (slot in slots) {
                    assertTrue(plan[slot.index].orEmpty().isNotEmpty(), "$hubType/$size has nobody on ${slot.name} $day")
                }
                val working = plan.values.flatten()
                assertEquals(working.size, working.toSet().size, "someone has two shifts on $day")
            }
            for (member in members) {
                val daysWorked = plans.take(7).count { plan -> plan.values.any { member in it } }
                assertEquals(6, daysWorked, "$hubType/$size member works $daysWorked days a week")
            }
        }
    }

    @Test
    fun `shifts rotate from one week to the next`() {
        val members = ShiftRotation.ordered(List(10) { UUID.randomUUID() })
        val first = ShiftRotation.plan(members, 2, monday.plusDays(2))
        val next = ShiftRotation.plan(members, 2, monday.plusDays(9))
        val movedToAfternoon = first[0].orEmpty().filter { it in next[1].orEmpty() }
        assertTrue(movedToAfternoon.isNotEmpty())
    }

    @Test
    fun `a driver checked in at the home warehouse covers the post offices on the route`() {
        val warehouse = UUID.randomUUID()
        val postOffice = UUID.randomUUID()
        val other = UUID.randomUUID()
        val route = listOf(warehouse, postOffice)

        assertTrue(ShiftCoverage.covers(WorkforceMemberKind.LINE_HAUL_DRIVER, postOffice, warehouse, route))
        assertFalse(ShiftCoverage.covers(WorkforceMemberKind.LINE_HAUL_DRIVER, other, warehouse, route))
        assertFalse(ShiftCoverage.covers(WorkforceMemberKind.COURIER, postOffice, warehouse, route))
        assertTrue(ShiftCoverage.covers(WorkforceMemberKind.COURIER, postOffice, postOffice, listOf(postOffice)))
    }
}
