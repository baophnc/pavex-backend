package com.pavex.identity.dto.role

data class ReplaceRolePermissionsRequest(
    val permissionCodes: Set<String> = emptySet(),
)
