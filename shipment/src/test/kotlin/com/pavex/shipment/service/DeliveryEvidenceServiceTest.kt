package com.pavex.shipment.service

import com.pavex.common.security.PavexActor
import com.pavex.shipment.entity.DeliveryEvidence
import com.pavex.shipment.exception.InvalidShipmentRequestException
import com.pavex.shipment.exception.ShipmentNotFoundException
import com.pavex.shipment.repository.DeliveryEvidenceRepository
import com.pavex.storage.ObjectStorage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import java.time.Instant
import java.util.Optional
import java.util.UUID

/** Proof-of-delivery photos live in object storage; older ones are still read from the database. */
class DeliveryEvidenceServiceTest {
    private val courier = PavexActor(UUID.randomUUID(), setOf("deliveries.complete-own"))
    private val objects = mutableMapOf<String, ByteArray>()
    private val rows = mutableMapOf<UUID, DeliveryEvidence>()

    private val storage = object : ObjectStorage {
        override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String) {
            objects["$bucket/$key"] = bytes
        }
        override fun get(bucket: String, key: String): ByteArray? = objects["$bucket/$key"]
        override fun delete(bucket: String, key: String) {
            objects.remove("$bucket/$key")
        }
    }

    private val service = DeliveryEvidenceService(
        mock(DeliveryEvidenceRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "save" -> invocation.getArgument<DeliveryEvidence>(0).also { rows[it.id] = it }
                "findById" -> Optional.ofNullable(rows[invocation.getArgument(0)])
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        storage,
        "pavex-evidence",
    )

    @Test
    fun `a new photo goes to object storage and only its key is kept in the database`() {
        val photo = byteArrayOf(1, 2, 3)

        val response = service.upload(courier, "image/jpeg", photo)

        val row = rows.values.single()
        assertEquals("evidence:${row.id}", response.reference)
        assertNull(row.data)
        assertTrue(row.objectKey!!.startsWith("evidence/"))
        assertArrayEquals(photo, objects["pavex-evidence/${row.objectKey}"])
        assertArrayEquals(photo, service.read(courier, row.id).bytes)
    }

    @Test
    fun `a photo stored before object storage is still served from the database`() {
        val old = DeliveryEvidence(
            uploadedByUserId = courier.userId,
            contentType = "image/png",
            sizeBytes = 2,
            sha256 = "0".repeat(64),
            data = byteArrayOf(9, 9),
            createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        )
        rows[old.id] = old

        val image = service.read(courier, old.id)

        assertEquals("image/png", image.contentType)
        assertArrayEquals(byteArrayOf(9, 9), image.bytes)
    }

    @Test
    fun `only images are accepted and other couriers cannot see them`() {
        assertThrows(InvalidShipmentRequestException::class.java) { service.upload(courier, "application/pdf", byteArrayOf(1)) }
        assertEquals(0, objects.size)

        service.upload(courier, "image/webp", byteArrayOf(1))
        val stranger = PavexActor(UUID.randomUUID(), setOf("deliveries.complete-own"))
        assertThrows(ShipmentNotFoundException::class.java) { service.read(stranger, rows.keys.single()) }
    }
}
