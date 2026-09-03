package com.pavex.identity.dto.role

import jakarta.validation.constraints.Size

data class ReplaceRolePermissionsRequest(
    @field:Size(max = 500) val permissionCodes: Set<@Size(min = 1, max = 160) String> = emptySet(),
)
