package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class InvalidPasswordResetTokenException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid_password_reset_token",
        message = "Invalid or expired password reset token.",
    )
