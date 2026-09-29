package com.pavex.partners.service

import com.pavex.partners.entity.SupportRequestKind
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Configuration
@EnableAsync
class SupportMailConfig

/**
 * Confirms receipt to the customer with their request code. Best effort: the request is already
 * saved, so a mail failure is logged and never reported back to the form.
 *
 * Anyone can submit the public form with any email address, so the receipt carries only Pavex's
 * own text and the code: echoing the submitted name or topic would let strangers send phishing
 * text to third parties from Pavex's address.
 */
@Component
class SupportRequestMailer(
    private val mailSender: ObjectProvider<JavaMailSender>,
    @Value("\${partners.support.mail.enabled:true}") private val enabled: Boolean,
    @Value("\${partners.support.mail.from:no-reply@pavex.local}") private val from: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSubmitted(event: SupportRequestSubmittedEvent) {
        val sender = mailSender.ifAvailable
        if (!enabled || sender == null) return
        try {
            sender.send(receipt(event, from))
        } catch (exception: Exception) {
            log.warn("Could not send the receipt for support request {}: {}", event.code, exception.message)
        }
    }

    companion object {
        fun receipt(event: SupportRequestSubmittedEvent, from: String) = SimpleMailMessage().apply {
            setFrom(from)
            setTo(event.email)
            subject = "[Pavex] Đã nhận ${kindLabel(event.kind)} ${event.code}"
            text = """
                Xin chào,

                Pavex đã nhận ${kindLabel(event.kind)} gửi từ website với mã ${event.code}.

                Bộ phận chăm sóc khách hàng sẽ phản hồi qua email hoặc số điện thoại đã cung cấp trong yêu cầu.
                Khi liên hệ lại với Pavex, vui lòng cung cấp mã yêu cầu ở trên.

                Nếu bạn không gửi yêu cầu này, hãy bỏ qua email.

                Pavex Express
            """.trimIndent()
        }

        private fun kindLabel(kind: SupportRequestKind) = when (kind) {
            SupportRequestKind.CONTACT -> "yêu cầu liên hệ"
            SupportRequestKind.SUPPORT -> "yêu cầu hỗ trợ"
            SupportRequestKind.CLAIM -> "khiếu nại"
            SupportRequestKind.CAREER -> "hồ sơ ứng tuyển"
        }
    }
}
