package com.pavex.auth.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AcceptInviteRequest(

    @field:NotBlank(
        message = "Invitation token is required",
    )
    @field:Size(
        max = 512,
        message = "Invitation token is invalid",
    )
    val token: String,

    @field:NotBlank(
        message = "Password is required",
    )
    @field:Pattern(
        regexp = PasswordRules.PATTERN,
        message = PasswordRules.MESSAGE,
    )
    val password: String,
)
