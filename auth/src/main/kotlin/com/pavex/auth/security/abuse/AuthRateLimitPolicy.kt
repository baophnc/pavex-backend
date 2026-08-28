package com.pavex.auth.security.abuse

import java.time.Duration

enum class AuthRateLimitPolicy(
    val code: String,
    val limit: Int,
    val window: Duration,
) {
    REGISTER_EMAIL(
        code = "register-email",
        limit = 5,
        window = Duration.ofMinutes(15),
    ),
    LOGIN_EMAIL(
        code = "login-email",
        limit = 10,
        window = Duration.ofMinutes(5),
    ),
    REFRESH_TOKEN(
        code = "refresh-token",
        limit = 30,
        window = Duration.ofMinutes(1),
    ),
    VERIFY_EMAIL_TOKEN(
        code = "verify-email-token",
        limit = 10,
        window = Duration.ofMinutes(10),
    ),
    RESEND_EMAIL(
        code = "resend-email",
        limit = 3,
        window = Duration.ofMinutes(10),
    ),
    FORGOT_PASSWORD_EMAIL(
        code = "forgot-password-email",
        limit = 3,
        window = Duration.ofMinutes(10),
    ),
    RESET_PASSWORD_TOKEN(
        code = "reset-password-token",
        limit = 5,
        window = Duration.ofMinutes(10),
    ),
    ACCEPT_INVITE_TOKEN(
        code = "accept-invite-token",
        limit = 5,
        window = Duration.ofMinutes(10),
    ),
    GOOGLE_TOKEN(
        code = "google-token",
        limit = 20,
        window = Duration.ofMinutes(1),
    ),
}
