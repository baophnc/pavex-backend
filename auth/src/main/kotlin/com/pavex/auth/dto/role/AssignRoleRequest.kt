package com.pavex.auth.dto.role

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AssignRoleRequest(

    @field:NotBlank(
        message = "Role code is required",
    )
    @field:Size(
        max = 64,
        message = "Role code must not exceed 64 characters",
    )
    val roleCode: String,
)
