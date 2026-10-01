package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(

    @field:NotBlank(
        message = "Current password is required",
    )
    @field:Size(
        max = 128,
        message = "Current password must not exceed 128 characters",
    )
    val currentPassword: String,

    @field:NotBlank(
        message = "New password is required",
    )
    @field:Pattern(
        regexp = PasswordRules.PATTERN,
        message = PasswordRules.MESSAGE,
    )
    val newPassword: String,
)
