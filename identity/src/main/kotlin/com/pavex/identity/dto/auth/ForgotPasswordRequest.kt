package com.pavex.identity.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ForgotPasswordRequest(

    @field:NotBlank(
        message = "Email is required",
    )
    @field:Email(
        message = "Email is invalid",
    )
    @field:Size(
        max = 255,
        message = "Email must not exceed 255 characters",
    )
    val email: String,
)
