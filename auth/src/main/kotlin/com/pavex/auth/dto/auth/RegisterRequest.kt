package com.pavex.auth.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(

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

    @field:NotBlank(
        message = "Password is required",
    )
    @field:Size(
        min = 8,
        max = 128,
        message = "Password must be between 8 and 128 characters",
    )
    val password: String,
)