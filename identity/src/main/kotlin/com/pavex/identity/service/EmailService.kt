package com.pavex.identity.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class EmailService(

    private val mailSender:
        JavaMailSender,

    @Value(
        "\${pavex.email.from:no-reply@pavex.local}"
    )
    private val fromEmail: String,

    @Value(
        "\${pavex.email.verification-url:http://localhost:3000/verify-email}"
    )
    private val verificationUrl: String,
) {

    fun sendEmailVerification(
        toEmail: String,
        rawToken: String,
    ) {

        val encodedToken =
            URLEncoder.encode(
                rawToken,
                StandardCharsets.UTF_8,
            )

        val verificationLink =
            "$verificationUrl?token=$encodedToken"

        val message =
            mailSender.createMimeMessage()

        val helper =
            MimeMessageHelper(
                message,
                "UTF-8",
            )

        helper.setFrom(
            fromEmail
        )

        helper.setTo(
            toEmail
        )

        helper.setSubject(
            "Verify your PAVEX email"
        )

        helper.setText(
            """
            <h2>Welcome to PAVEX</h2>

            <p>Please verify your email address.</p>

            <p>
                <a href="$verificationLink">
                    Verify email
                </a>
            </p>

            <p>This link expires in 24 hours.</p>
            """.trimIndent(),
            true,
        )

        mailSender.send(
            message
        )
    }
}
