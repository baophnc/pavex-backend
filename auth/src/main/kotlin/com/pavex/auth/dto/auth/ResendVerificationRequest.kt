package com.pavex.auth.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResendVerificationRequest(
    @field:NotBlank @field:Email @field:Size(max = 320) val email: String,
)
