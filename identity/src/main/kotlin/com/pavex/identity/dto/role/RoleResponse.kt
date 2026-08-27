package com.pavex.identity.dto.role

import java.util.UUID

data class RoleResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val description: String?,
    val isSystem: Boolean,
    val permissions: List<PermissionResponse>,
)
