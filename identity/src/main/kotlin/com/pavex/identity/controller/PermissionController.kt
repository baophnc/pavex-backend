package com.pavex.identity.controller

import com.pavex.identity.dto.role.PermissionResponse
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.RoleService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/permissions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Permissions", description = "Read-only permission capability catalog")
class PermissionController(
    private val roleService: RoleService,
) {
    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.READ_ROLES}')"
    )
    @GetMapping
    fun getPermissions(): List<PermissionResponse> =
        roleService.getPermissions()
}
