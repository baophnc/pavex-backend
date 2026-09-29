package com.pavex.partners.service

import com.pavex.partners.dto.SubmitSupportRequest
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.exception.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SupportRequestIntakeTest {
    private val today = LocalDate.parse("2026-09-27")

    @Test
    fun `a claim keeps its claim fields and drops the career link`() {
        val normalized = SupportRequestIntake.normalize(
            claim().copy(email = " An@Example.COM ", trackingCode = "pavex260927abc", portfolioUrl = "https://example.com"),
            today,
        )

        assertEquals("an@example.com", normalized.email)
        assertEquals("PAVEX260927ABC", normalized.trackingCode)
        assertEquals(LocalDate.parse("2026-09-26"), normalized.incidentDate)
        assertNull(normalized.portfolioUrl)
    }

    @Test
    fun `a contact request ignores claim-only fields`() {
        val normalized = SupportRequestIntake.normalize(
            claim().copy(kind = SupportRequestKind.CONTACT, trackingCode = null, claimedAmount = 500_000),
            today,
        )

        assertNull(normalized.incidentDate)
        assertNull(normalized.claimedAmount)
    }

    @Test
    fun `claims need a tracking code and a past incident date`() {
        assertEquals("invalid_support_request", rejected(claim().copy(trackingCode = null)))
        assertEquals("invalid_support_request", rejected(claim().copy(incidentDate = null)))
        assertEquals("invalid_support_request", rejected(claim().copy(incidentDate = today.plusDays(1))))
    }

    @Test
    fun `short messages, bad emails and non-web portfolio links are rejected`() {
        assertEquals("invalid_support_request", rejected(claim().copy(message = "Quá ngắn")))
        assertEquals("invalid_support_request", rejected(claim().copy(email = "not-an-email")))
        assertEquals(
            "invalid_support_request",
            rejected(claim().copy(kind = SupportRequestKind.CAREER, trackingCode = null, portfolioUrl = "javascript:alert(1)")),
        )
    }

    @Test
    fun `file types come from their bytes, not their names`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val pdf = "%PDF-1.7 test".toByteArray()
        val html = "<html>".toByteArray()

        val files = SupportRequestIntake.normalizeFiles(
            SupportRequestKind.CLAIM,
            listOf(IncomingSupportFile("../../hàng vỡ.png", jpeg), IncomingSupportFile(null, pdf)),
        )

        assertEquals(listOf("hàng vỡ.jpg", "tep-2.pdf"), files.map { it.fileName })
        assertEquals(listOf("image/jpeg", "application/pdf"), files.map { it.contentType })
        val error = assertThrows(ApiException::class.java) {
            SupportRequestIntake.normalizeFiles(SupportRequestKind.CLAIM, listOf(IncomingSupportFile("anh.jpg", html)))
        }
        assertEquals("support_attachment_invalid", error.code)
    }

    @Test
    fun `only claims take attachments, at most five`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val notAllowed = assertThrows(ApiException::class.java) {
            SupportRequestIntake.normalizeFiles(SupportRequestKind.CONTACT, listOf(IncomingSupportFile("a.jpg", jpeg)))
        }
        assertEquals("support_attachments_not_allowed", notAllowed.code)

        val tooMany = assertThrows(ApiException::class.java) {
            SupportRequestIntake.normalizeFiles(SupportRequestKind.CLAIM, List(6) { IncomingSupportFile("a.jpg", jpeg) })
        }
        assertEquals("support_attachment_invalid", tooMany.code)
        assertTrue(SupportRequestIntake.normalizeFiles(SupportRequestKind.CONTACT, emptyList()).isEmpty())
    }

    @Test
    fun `request codes are short and readable`() {
        val code = SupportRequestCodes.next(java.time.Instant.parse("2026-09-26T18:30:00Z"))

        assertTrue(Regex("^YC260927-[2-9A-HJKMNP-TV-Z]{5}$").matches(code), code)
    }

    private fun rejected(request: SubmitSupportRequest): String =
        assertThrows(ApiException::class.java) { SupportRequestIntake.normalize(request, today) }.code

    private fun claim() = SubmitSupportRequest(
        kind = SupportRequestKind.CLAIM,
        fullName = "Nguyễn Văn An",
        email = "an@example.com",
        phone = "0901 234 567",
        topic = "Hàng hóa hư hỏng",
        trackingCode = "PAVEX260927ABC",
        incidentDate = LocalDate.parse("2026-09-26"),
        claimedAmount = 250_000,
        message = "Kiện hàng đến nơi bị móp méo và vỡ hai món bên trong.",
    )
}
