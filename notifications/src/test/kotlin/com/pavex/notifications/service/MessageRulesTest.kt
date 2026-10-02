package com.pavex.notifications.service

import com.pavex.contracts.operations.WorkPersonSnapshot
import com.pavex.notifications.entity.MessageAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class MessageRulesTest {
    private val hanoi = UUID.randomUUID()
    private val saigon = UUID.randomUUID()

    private fun person(kind: String?, vararg hubs: UUID) = WorkPersonSnapshot(UUID.randomUUID(), kind, hubs.toList())

    @Test
    fun `a hub manager writes to everyone at their own hub only`() {
        val manager = person("HUB_MANAGER", hanoi)

        assertTrue(MessageRules.mayWriteToHub(false, manager, MessageAudience.HUB_STAFF, hanoi))
        assertTrue(MessageRules.mayWriteToHub(false, manager, MessageAudience.HUB_EVERYONE, hanoi))
        assertFalse(MessageRules.mayWriteToHub(false, manager, MessageAudience.HUB_STAFF, saigon))
    }

    @Test
    fun `hub staff write to couriers and drivers but not to other staff`() {
        val staff = person("WAREHOUSE_OPERATOR", hanoi)

        assertEquals(listOf(MessageAudience.HUB_COURIERS, MessageAudience.HUB_DRIVERS), MessageRules.audiences(false, "WAREHOUSE_OPERATOR"))
        assertTrue(MessageRules.mayWriteToHub(false, staff, MessageAudience.HUB_COURIERS, hanoi))
        assertFalse(MessageRules.mayWriteToHub(false, staff, MessageAudience.HUB_STAFF, hanoi))
    }

    @Test
    fun `couriers broadcast nothing but may write to the shop of a shipment their hub handles`() {
        val courier = person("COURIER", saigon)

        assertTrue(MessageRules.audiences(false, "COURIER").isEmpty())
        assertTrue(MessageRules.mayWriteAboutShipment(false, courier, setOf(hanoi, saigon)))
        assertFalse(MessageRules.mayWriteAboutShipment(false, courier, setOf(hanoi, null)))
    }

    @Test
    fun `head office writes anywhere, a customer nowhere`() {
        assertTrue(MessageRules.mayWriteToHub(true, person(null), MessageAudience.HUB_EVERYONE, saigon))
        assertFalse(MessageRules.mayMessageShops(false, null))
    }
}
