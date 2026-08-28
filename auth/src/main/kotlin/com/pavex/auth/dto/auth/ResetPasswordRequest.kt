package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
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
    @field:Size(
        min = 8,
        max = 128,
        message = "Password must be between 8 and 128 characters",
    )
    val newPassword: String,
)
