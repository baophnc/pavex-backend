package com.pavex.auth.controller

import com.pavex.auth.dto.role.RoleResponse
import com.pavex.auth.security.AuthorizationPermissions
import com.pavex.auth.service.RoleService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/roles")
class RoleController(

    private val roleService:
        RoleService,
) {

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Auth.READ_ROLES}')"
    )
    @GetMapping
    fun getRoles():
        List<RoleResponse> {

        return roleService
            .getRoles()
    }
}
