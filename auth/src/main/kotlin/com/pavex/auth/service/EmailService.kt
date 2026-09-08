package com.pavex.auth.service

import com.pavex.auth.event.PasswordResetPortal

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
    @Value("\${pavex.email.invitation-url}")
    private val invitationUrl: String,
    @Value("\${pavex.email.management-password-reset-url:http://localhost:3001/reset-password}")
    private val managementPasswordResetUrl: String,
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
        portal: PasswordResetPortal = PasswordResetPortal.CLIENT,
    ) {
        val encodedToken =
            URLEncoder.encode(
                rawToken,
                StandardCharsets.UTF_8,
            )

        val destination = when (portal) {
            PasswordResetPortal.CLIENT -> passwordResetUrl
            PasswordResetPortal.MANAGEMENT -> managementPasswordResetUrl
        }
        val resetLink = "$destination?token=$encodedToken"

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
            "PAVEX — Thiết lập mật khẩu của bạn"
        )

        helper.setText(
            """
            <html>
            <body>
                <h2>Thiết lập mật khẩu PAVEX</h2>

                <p>
                    Bạn vừa yêu cầu tạo mới hoặc đặt lại mật khẩu tài khoản PAVEX.
                    Nếu tài khoản do quản trị viên tạo, đây cũng là bước xác minh
                    email để bạn bắt đầu sử dụng tài khoản.
                </p>

                <p>
                    <a href="$resetLink">
                        Đặt mật khẩu mới
                    </a>
                </p>

                <p>
                    Liên kết chỉ dùng một lần và có hiệu lực trong 1 giờ.
                </p>

                <p>
                    Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email.
                    Không chia sẻ liên kết với người khác.
                </p>
            </body>
            </html>
            """.trimIndent(),
            true,
        )

        mailSender.send(message)
    }
    fun sendInvitationEmail(
        to: String,
        rawToken: String,
    ) {

        val invitationLink =
            "$invitationUrl?token=$rawToken"

        val message =
            mailSender.createMimeMessage()

        val helper =
            MimeMessageHelper(
                message,
                true,
                "UTF-8",
            )

        helper.setFrom(
            from
        )

        helper.setTo(
            to
        )

        helper.setSubject(
            "You have been invited to PAVEX"
        )

        helper.setText(
            """
        <h2>You have been invited to PAVEX</h2>

        <p>
            An account has been created for you in PAVEX.
        </p>

        <p>
            Use the link below to activate your account
            and create your password.
        </p>

        <p>
            <a href="$invitationLink">
                Accept invitation
            </a>
        </p>

        <p>
            This invitation expires in 24 hours.
        </p>

        <p>
            If you were not expecting this invitation,
            you can ignore this email.
        </p>
        """.trimIndent(),
            true,
        )

        mailSender.send(
            message
        )
    }
}
