package com.pavex.network.service

import com.pavex.network.entity.Hub
import com.pavex.network.exception.ConflictException
import com.pavex.network.repository.HubRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

class HubHierarchyServiceTest {
    private val hubs = mock(HubRepository::class.java)
    private val service = HubHierarchyService(hubs)

    @Test
    fun `rejects direct self parent`() {
        val id = UUID.randomUUID()
        assertThrows(ConflictException::class.java) {
            service.validateParent(id, id)
        }
    }

    @Test
    fun `allows parent independent of hub type`() {
        val child = UUID.randomUUID()
        val parent = Hub(id = UUID.randomUUID())
        `when`(hubs.findById(parent.id)).thenReturn(Optional.of(parent))
        service.validateParent(child, parent.id)
    }
}
