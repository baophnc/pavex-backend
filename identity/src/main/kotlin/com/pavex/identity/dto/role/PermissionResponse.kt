package com.pavex.identity.dto.role

import java.util.UUID

data class PermissionResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
)
