package com.pavex.identity.service

import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class EmailService(

    private val mailSender: JavaMailSender,

    @Value("\${pavex.email.from:no-reply@pavex.local}")
    private val from: String,

    @Value(
        "\${pavex.email.verification-url:http://localhost:3000/verify-email}"
    )
    private val verificationUrl: String,

    @Value(
        "\${pavex.email.password-reset-url:http://localhost:3000/reset-password}"
    )
    private val passwordResetUrl: String,
) {

    fun sendVerificationEmail(
        to: String,
        rawToken: String,
    ) {
        val encodedToken =
            URLEncoder.encode(
                rawToken,
                StandardCharsets.UTF_8,
            )

        val verificationLink =
            "$verificationUrl?token=$encodedToken"

        val message: MimeMessage =
            mailSender.createMimeMessage()

        val helper =
            MimeMessageHelper(
                message,
                true,
                "UTF-8",
            )

        helper.setFrom(from)
        helper.setTo(to)
        helper.setSubject(
            "Verify your PAVEX email"
        )

        helper.setText(
            """
            <html>
            <body>
                <h2>Verify your PAVEX email</h2>

                <p>
                    Welcome to PAVEX.
                </p>

                <p>
                    Please verify your email address
                    by clicking the link below.
                </p>

                <p>
                    <a href="$verificationLink">
                        Verify email
                    </a>
                </p>

                <p>
                    This verification link expires
                    after 24 hours.
                </p>
            </body>
            </html>
            """.trimIndent(),
            true,
        )

        mailSender.send(message)
    }


    fun sendPasswordResetEmail(
        to: String,
        rawToken: String,
    ) {
        val encodedToken =
            URLEncoder.encode(
                rawToken,
                StandardCharsets.UTF_8,
            )

        val resetLink =
            "$passwordResetUrl?token=$encodedToken"

        val message: MimeMessage =
            mailSender.createMimeMessage()

        val helper =
            MimeMessageHelper(
                message,
                true,
                "UTF-8",
            )

        helper.setFrom(from)
        helper.setTo(to)

        helper.setSubject(
            "Reset your PAVEX password"
        )

        helper.setText(
            """
            <html>
            <body>
                <h2>Reset your PAVEX password</h2>

                <p>
                    We received a request to reset
                    your PAVEX account password.
                </p>

                <p>
                    <a href="$resetLink">
                        Reset password
                    </a>
                </p>

                <p>
                    This link expires in 1 hour.
                </p>

                <p>
                    If you did not request this,
                    you can safely ignore this email.
                </p>
            </body>
            </html>
            """.trimIndent(),
            true,
        )

        mailSender.send(message)
    }
}
