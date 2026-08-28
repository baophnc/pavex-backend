package com.pavex.identity.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import com.pavex.identity.dto.role.RoleResponse
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.RoleService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/roles")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles", description = "Role catalog")
class RoleController(

    private val roleService:
        RoleService,
) {

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.READ_ROLES}')"
    )
    @GetMapping
    fun getRoles():
        List<RoleResponse> {

        return roleService
            .getRoles()
    }
}
