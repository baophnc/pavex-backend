package com.pavex.identity.dto.auth

import jakarta.validation.constraints.NotBlank
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
    @field:Size(
        min = 8,
        max = 128,
        message = "New password must be between 8 and 128 characters",
    )
    val newPassword: String,
)
