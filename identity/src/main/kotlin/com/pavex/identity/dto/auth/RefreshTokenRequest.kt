package com.pavex.identity.dto.auth

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(

    @field:NotBlank
    val refreshToken: String,
)