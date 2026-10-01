package com.pavex.auth.service

import com.pavex.auth.event.PasswordResetPortal
import com.pavex.auth.event.PasswordResetRequestedEvent
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.mail.javamail.JavaMailSender
import java.io.ByteArrayOutputStream
import java.util.Properties

class PasswordResetEmailTest {
    @Test
    fun `reset link uses the configured management or client portal`() {
        for (portal in PasswordResetPortal.entries) {
            val message = MimeMessage(Session.getInstance(Properties()))
            val sender = mock(JavaMailSender::class.java)
            `when`(sender.createMimeMessage()).thenReturn(message)
            val email = EmailService(sender, "no-reply@pavex.local", "http://localhost:3000/verify-email",
                "http://localhost:3000/reset-password", "http://localhost:3000/accept-invite",
                "http://localhost:3001/reset-password", "http://localhost:3001/accept-invite")
            email.sendPasswordResetEmail("ngocmai@demo.pavex.local", "test-token", portal)
            verify(sender).send(message)
            message.saveChanges()
            val output = ByteArrayOutputStream()
            message.writeTo(output)
            // MIME may encode Vietnamese, but ASCII URL and token must remain decodable.
            val root = message.content as jakarta.mail.Multipart
            val alternative = root.getBodyPart(0).content as jakarta.mail.Multipart
            val html = alternative.getBodyPart(0).content.toString()
            val port = if (portal == PasswordResetPortal.MANAGEMENT) 3001 else 3000
            assertTrue(html.contains("http://localhost:$port/reset-password?token=test-token"))
            assertTrue(message.subject.contains("mật khẩu"))
        }
    }

    @Test
    fun `legacy callers default to the client portal`() {
        assertEquals(PasswordResetPortal.CLIENT, PasswordResetRequestedEvent("a@demo.pavex.local", "token").portal)
    }
}
