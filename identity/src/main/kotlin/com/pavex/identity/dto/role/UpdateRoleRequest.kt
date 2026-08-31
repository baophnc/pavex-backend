package com.pavex.identity.dto.role

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateRoleRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 128)
    val name: String,

    @field:Size(max = 512)
    val description: String? = null,
)
