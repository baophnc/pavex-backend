package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VerifyEmailRequest(
    @field:NotBlank @field:Size(max = 4096) val token: String,
)
