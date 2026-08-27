package com.pavex.identity.dto.auth

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
    @field:Size(
        min = 8,
        max = 128,
        message = "Password must be between 8 and 128 characters",
    )
    @field:Pattern(
        regexp =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,128}$",
        message =
            "Password must contain uppercase, lowercase, number and special character",
    )
    val password: String,
)
