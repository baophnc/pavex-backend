package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GoogleLoginRequest(

    @field:NotBlank(
        message = "Google ID token is required",
    )
    @field:Size(
        max = 8192,
        message = "Google ID token is invalid",
    )
    val idToken: String,
)
