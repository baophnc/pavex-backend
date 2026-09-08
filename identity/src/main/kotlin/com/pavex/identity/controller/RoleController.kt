package com.pavex.identity.controller

import com.pavex.identity.dto.role.CreateRoleRequest
import com.pavex.identity.dto.role.ReplaceRolePermissionsRequest
import com.pavex.identity.dto.role.RoleResponse
import com.pavex.identity.dto.role.UpdateRoleRequest
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.RoleService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/roles")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles", description = "Role catalog and custom-role management")
class RoleController(
    private val roleService: RoleService,
) {
    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.READ_ROLES}')"
    )
    @GetMapping
    fun getRoles(): List<RoleResponse> =
        roleService.getRoles()

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_ROLES}') and hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN','IAM_ADMIN')"
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRole(
        authentication: JwtAuthenticationToken,
        @Valid @RequestBody request: CreateRoleRequest,
    ): RoleResponse =
        roleService.createRoleAuthorized(actor(authentication), request)

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_ROLES}') and hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN','IAM_ADMIN')"
    )
    @PatchMapping("/{roleId}")
    fun updateRole(
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: UpdateRoleRequest,
    ): RoleResponse =
        roleService.updateRole(roleId, request)

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_ROLES}') and hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN','IAM_ADMIN')"
    )
    @PutMapping("/{roleId}/permissions")
    fun replacePermissions(
        authentication: JwtAuthenticationToken,
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: ReplaceRolePermissionsRequest,
    ): RoleResponse =
        roleService.replacePermissionsAuthorized(actor(authentication), roleId, request)

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_ROLES}') and hasAnyRole('ADMIN','PLATFORM_SUPER_ADMIN','IAM_ADMIN')"
    )
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRole(
        @PathVariable roleId: UUID,
    ) {
        roleService.deleteRole(roleId)
    }

    private fun actor(authentication: JwtAuthenticationToken): UUID = UUID.fromString(authentication.token.subject)
}
