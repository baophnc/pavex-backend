package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ResetPasswordRequest(

    @field:NotBlank(
        message = "Token is required",
    )
    @field:Size(
        max = 512,
        message = "Token is invalid",
    )
    val token: String,

    @field:NotBlank(
        message = "New password is required",
    )
    @field:Pattern(
        regexp = PasswordRules.PATTERN,
        message = PasswordRules.MESSAGE,
    )
    val newPassword: String,
)
