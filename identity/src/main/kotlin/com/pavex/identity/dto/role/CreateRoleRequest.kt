package com.pavex.identity.dto.role

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateRoleRequest(
    @field:NotBlank
    @field:Pattern(
        regexp = "^[A-Z][A-Z0-9_]{2,63}$",
        message = "Role code must start with a letter and contain only A-Z, 0-9 or underscore",
    )
    val code: String,

    @field:NotBlank
    @field:Size(min = 2, max = 128)
    val name: String,

    @field:Size(max = 512)
    val description: String? = null,

    val permissionCodes: Set<String> = emptySet(),
)
