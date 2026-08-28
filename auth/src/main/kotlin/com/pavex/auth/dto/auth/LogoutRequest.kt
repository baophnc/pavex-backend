package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank

data class LogoutRequest(

    @field:NotBlank
    val refreshToken: String,
)