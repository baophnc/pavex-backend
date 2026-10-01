package com.pavex.partners.service

import com.pavex.partners.dto.AssignSupportRequest
import com.pavex.partners.dto.ChangeSupportRequestStatus
import com.pavex.partners.dto.SubmitSupportRequest
import com.pavex.partners.entity.SupportRequest
import com.pavex.partners.entity.SupportRequestAction
import com.pavex.partners.entity.SupportRequestAttachment
import com.pavex.partners.entity.SupportRequestEvent
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.entity.SupportRequestStatus
import com.pavex.partners.exception.ApiException
import com.pavex.partners.repository.SupportRequestAttachmentRepository
import com.pavex.partners.repository.SupportRequestEventRepository
import com.pavex.partners.repository.SupportRequestRepository
import com.pavex.storage.ObjectStorage
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class SupportRequestServiceTest {
    private val requests = linkedMapOf<UUID, SupportRequest>()
    private val attachments = mutableListOf<SupportRequestAttachment>()
    private val events = mutableListOf<SupportRequestEvent>()
    private val published = mutableListOf<Any>()
    private val stored = mutableMapOf<String, ByteArray>()
    private val storage = object : ObjectStorage {
        override fun put(bucket: String, key: String, bytes: ByteArray, contentType: String) {
            stored["$bucket/$key"] = bytes
        }
        override fun get(bucket: String, key: String): ByteArray? = stored["$bucket/$key"]
        override fun delete(bucket: String, key: String) {
            stored.remove("$bucket/$key")
        }
    }
    private val service = SupportRequestService(
        requestRepository(),
        attachmentRepository(),
        eventRepository(),
        storage,
        "pavex-attachments",
    ) { published += it }

    @Test
    fun `a claim is saved with its evidence and a receipt is queued`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01)

        val receipt = service.submit(null, claim(), listOf(IncomingSupportFile("vo.jpg", jpeg)))

        val saved = requests.values.single()
        assertEquals(receipt.code, saved.code)
        assertEquals(SupportRequestStatus.NEW, saved.status)
        assertEquals(saved.id, attachments.single().requestId)
        // The file itself is in object storage; the database only points at it.
        assertEquals(null, attachments.single().data)
        assertArrayEquals(jpeg, stored["pavex-attachments/${attachments.single().objectKey}"])
        assertEquals(SupportRequestAction.SUBMITTED, events.single().action)
        assertEquals(receipt.code, (published.single() as SupportRequestSubmittedEvent).code)
    }

    @Test
    fun `a filled honeypot gets a receipt but nothing is stored`() {
        val receipt = service.submit(null, claim().copy(website = "http://spam.example"), emptyList())

        assertTrue(receipt.code.startsWith("YC"))
        assertTrue(requests.isEmpty())
        assertTrue(published.isEmpty())
    }

    @Test
    fun `one email can send at most five requests an hour`() {
        repeat(5) { service.submit(null, claim(), emptyList()) }

        val error = assertThrows(ApiException::class.java) { service.submit(null, claim(), emptyList()) }

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.status)
        assertEquals(5, requests.size)
    }

    @Test
    fun `taking a request starts it, and closing it needs a note`() {
        val staff = UUID.randomUUID()
        service.submit(null, claim(), emptyList())
        val id = requests.keys.single()

        val taken = service.assign(id, staff, AssignSupportRequest(staff))
        assertEquals(SupportRequestStatus.IN_PROGRESS, taken.status)
        assertEquals(staff, taken.assigneeUserId)

        val noNote = assertThrows(ApiException::class.java) {
            service.changeStatus(id, staff, ChangeSupportRequestStatus(SupportRequestStatus.RESOLVED))
        }
        assertEquals("support_request_state", noNote.code)

        val resolved = service.changeStatus(id, staff, ChangeSupportRequestStatus(SupportRequestStatus.RESOLVED, "Đã hoàn tiền 250.000đ"))
        assertEquals(SupportRequestStatus.RESOLVED, resolved.status)
        assertEquals("Đã hoàn tiền 250.000đ", resolved.resolutionNote)

        val reopened = service.changeStatus(id, staff, ChangeSupportRequestStatus(SupportRequestStatus.IN_PROGRESS))
        assertNull(reopened.closedAt)
    }

    @Test
    fun `a request someone else holds cannot be taken`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        service.submit(null, claim(), emptyList())
        val id = requests.keys.single()
        service.assign(id, first, AssignSupportRequest(first))

        val error = assertThrows(ApiException::class.java) { service.assign(id, second, AssignSupportRequest(second)) }

        assertEquals("support_request_taken", error.code)
        assertEquals(first, requests.getValue(id).assigneeUserId)
    }

    @Test
    fun `handing a request back returns it to the shared queue`() {
        val staff = UUID.randomUUID()
        service.submit(null, claim(), emptyList())
        val id = requests.keys.single()
        service.assign(id, staff, AssignSupportRequest(staff))

        val released = service.assign(id, staff, AssignSupportRequest(null))

        assertEquals(SupportRequestStatus.NEW, released.status)
        assertNull(released.assigneeUserId)
        assertEquals(SupportRequestAction.UNASSIGNED, events.last().action)
    }

    @Test
    fun `the receipt email never repeats what the visitor typed`() {
        service.submit(null, claim().copy(fullName = "Bấm vào http://lua-dao.example", topic = "Tài khoản bị khoá"), emptyList())
        val event = published.single() as SupportRequestSubmittedEvent

        val message = SupportRequestMailer.receipt(event, "no-reply@pavex.local")

        assertEquals("an@example.com", message.to?.single())
        assertTrue(message.text!!.contains(event.code))
        assertFalse(message.text!!.contains("lua-dao"))
        assertFalse(message.text!!.contains("Tài khoản bị khoá"))
    }

    @Test
    fun `staff can only take a request for themselves`() {
        service.submit(null, claim(), emptyList())
        val id = requests.keys.single()

        val error = assertThrows(ApiException::class.java) {
            service.assign(id, UUID.randomUUID(), AssignSupportRequest(UUID.randomUUID()))
        }

        assertEquals("support_assign_self_only", error.code)
    }

    private fun claim() = SubmitSupportRequest(
        kind = SupportRequestKind.CLAIM,
        fullName = "Nguyễn Văn An",
        email = "an@example.com",
        topic = "Hàng hóa hư hỏng",
        trackingCode = "PAVEX260927ABC",
        incidentDate = LocalDate.parse("2026-09-01"),
        message = "Kiện hàng đến nơi bị móp méo và vỡ hai món bên trong.",
    )

    private fun requestRepository(): SupportRequestRepository = proxy { name, args ->
        when (name) {
            "save", "saveAndFlush" -> (args[0] as SupportRequest).also { requests[it.id] = it }
            "findById" -> Optional.ofNullable(requests[args[0] as UUID])
            "existsByCode" -> requests.values.any { it.code == args[0] }
            "countByEmailAndCreatedAtAfter" -> requests.values.count { it.email == args[0] && it.createdAt.isAfter(args[1] as Instant) }.toLong()
            else -> error("Unexpected SupportRequestRepository.$name")
        }
    }

    private fun attachmentRepository(): SupportRequestAttachmentRepository = proxy { name, args ->
        when (name) {
            "saveAll" -> (args[0] as Iterable<*>).map { it as SupportRequestAttachment }.also(attachments::addAll)
            "findMetadata" -> emptyList<Any>()
            else -> error("Unexpected SupportRequestAttachmentRepository.$name")
        }
    }

    private fun eventRepository(): SupportRequestEventRepository = proxy { name, args ->
        when (name) {
            "save" -> (args[0] as SupportRequestEvent).also(events::add)
            "findAllByRequestIdOrderByCreatedAtAsc" -> events.filter { it.requestId == args[0] }
            else -> error("Unexpected SupportRequestEventRepository.$name")
        }
    }

    private inline fun <reified T> proxy(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { self, method, args ->
            when (method.name) {
                "toString" -> T::class.java.simpleName
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T
}
