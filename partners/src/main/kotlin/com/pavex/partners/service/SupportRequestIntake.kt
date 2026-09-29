package com.pavex.partners.service

import com.pavex.partners.dto.SubmitSupportRequest
import com.pavex.partners.entity.SupportRequestKind
import com.pavex.partners.exception.ApiException
import org.springframework.http.HttpStatus
import java.net.URI
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A file part as received, before its real type is checked. */
class IncomingSupportFile(val originalName: String?, val bytes: ByteArray)

data class NormalizedSupportRequest(
    val kind: SupportRequestKind,
    val fullName: String,
    val email: String,
    val phone: String?,
    val topic: String,
    val trackingCode: String?,
    val incidentDate: LocalDate?,
    val claimedAmount: Long?,
    val portfolioUrl: String?,
    val message: String,
)

data class NormalizedSupportFile(val fileName: String, val contentType: String, val bytes: ByteArray)

/**
 * Validates what the public forms send. Rules mirror the client form so a request the browser
 * accepts is never rejected here, and fields that do not belong to the request kind are dropped.
 */
object SupportRequestIntake {
    const val MAX_ATTACHMENTS = 5
    const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
    val VIETNAM: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    private val phonePattern = Regex("^[0-9+() .-]{8,20}$")
    private val trackingPattern = Regex("^PAVEX[A-Z0-9-]{1,35}$")
    private const val MAX_CLAIM_AMOUNT = 1_000_000_000_000L

    fun normalize(request: SubmitSupportRequest, today: LocalDate): NormalizedSupportRequest {
        val kind = request.kind
        val fullName = text(request.fullName, 1, 100, "Họ và tên")
        val email = request.email.trim().lowercase()
        if (email.length > 160 || !emailPattern.matches(email)) invalid("Email không hợp lệ.")
        val phone = request.phone?.trim()?.takeIf { it.isNotEmpty() }
        if (phone != null && !phonePattern.matches(phone)) invalid("Số điện thoại không hợp lệ.")
        val topic = text(request.topic, 1, 100, "Chủ đề")
        val message = text(request.message, 20, 2000, "Nội dung")

        val trackingCode = request.trackingCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
            ?.takeUnless { kind == SupportRequestKind.CAREER }
        if (trackingCode != null && !trackingPattern.matches(trackingCode)) invalid("Mã vận đơn phải bắt đầu bằng PAVEX.")

        var incidentDate: LocalDate? = null
        var claimedAmount: Long? = null
        if (kind == SupportRequestKind.CLAIM) {
            if (trackingCode == null) invalid("Khiếu nại cần mã vận đơn.")
            incidentDate = request.incidentDate ?: invalid("Khiếu nại cần ngày phát hiện sự việc.")
            if (incidentDate.isAfter(today)) invalid("Ngày phát hiện không được ở tương lai.")
            claimedAmount = request.claimedAmount
            if (claimedAmount != null && claimedAmount !in 0..MAX_CLAIM_AMOUNT) invalid("Giá trị đề nghị đối soát không hợp lệ.")
        }

        val portfolioUrl = request.portfolioUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?.takeIf { kind == SupportRequestKind.CAREER }
        if (portfolioUrl != null && !isWebUrl(portfolioUrl)) invalid("Đường dẫn hồ sơ phải là địa chỉ http hoặc https.")

        return NormalizedSupportRequest(
            kind, fullName, email, phone, topic, trackingCode, incidentDate, claimedAmount, portfolioUrl, message,
        )
    }

    fun normalizeFiles(kind: SupportRequestKind, files: List<IncomingSupportFile>): List<NormalizedSupportFile> {
        val present = files.filter { it.bytes.isNotEmpty() || !it.originalName.isNullOrBlank() }
        if (present.isEmpty()) return emptyList()
        if (kind != SupportRequestKind.CLAIM) {
            throw ApiException(HttpStatus.BAD_REQUEST, "support_attachments_not_allowed", "Chỉ khiếu nại mới được đính kèm tệp.")
        }
        if (present.size > MAX_ATTACHMENTS) attachmentInvalid("Chỉ được đính kèm tối đa $MAX_ATTACHMENTS tệp.")
        return present.mapIndexed { index, file ->
            if (file.bytes.isEmpty() || file.bytes.size > MAX_ATTACHMENT_BYTES) attachmentInvalid("Mỗi tệp phải nhỏ hơn 5 MB.")
            val type = detectType(file.bytes) ?: attachmentInvalid("Chỉ nhận ảnh JPG, PNG, WEBP hoặc tệp PDF.")
            NormalizedSupportFile(fileName(file.originalName, index + 1, type), type, file.bytes)
        }
    }

    /** Trusts the file's leading bytes, not the name or the browser's content type. */
    fun detectType(bytes: ByteArray): String? {
        fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { (bytes[it].toInt() and 0xFF) == prefix[it] }
        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
            startsWith(0x25, 0x50, 0x44, 0x46, 0x2D) -> "application/pdf"
            else -> null
        }
    }

    private fun fileName(original: String?, sequence: Int, type: String): String {
        val extension = when (type) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "pdf"
        }
        val base = original.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filterNot { it.isISOControl() || it in "\"<>|:*?" }
            .trim()
            .substringBeforeLast('.')
            .take(150)
        return "${base.ifEmpty { "tep-$sequence" }}.$extension"
    }

    private fun isWebUrl(value: String): Boolean = value.length <= 500 && runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun text(value: String, min: Int, max: Int, label: String): String {
        val normalized = value.trim()
        if (normalized.length !in min..max) invalid("$label cần từ $min đến $max ký tự.")
        return normalized
    }

    private fun invalid(message: String): Nothing =
        throw ApiException(HttpStatus.BAD_REQUEST, "invalid_support_request", message)

    private fun attachmentInvalid(message: String): Nothing =
        throw ApiException(HttpStatus.BAD_REQUEST, "support_attachment_invalid", message)
}

/** Short codes customers can read out on the phone, e.g. YC260927-7K3QX. */
object SupportRequestCodes {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()
    private val date = DateTimeFormatter.ofPattern("yyMMdd").withZone(SupportRequestIntake.VIETNAM)

    fun next(now: Instant): String =
        "YC${date.format(now)}-" + (1..5).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
}
