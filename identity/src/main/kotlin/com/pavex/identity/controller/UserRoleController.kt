package com.pavex.identity.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import com.pavex.identity.dto.role.AssignRoleRequest
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.RoleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "User Roles", description = "Role assignments for users")
class UserRoleController(

    private val roleService:
        RoleService,
) {

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.ASSIGN_ROLE}')"
    )
    @PostMapping(
        "/{userId}/roles"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    fun assignRole(
        authentication:
            JwtAuthenticationToken,

        @PathVariable
        userId: UUID,

        @Valid
        @RequestBody
        request: AssignRoleRequest,
    ) {
        roleService.assignRole(
            actorUserId =
                UUID.fromString(
                    authentication
                        .token
                        .subject
                ),

            targetUserId =
                userId,

            rawRoleCode =
                request.roleCode,
        )
    }


    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.ASSIGN_ROLE}')"
    )
    @DeleteMapping(
        "/{userId}/roles/{roleCode}"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    fun removeRole(
        authentication:
            JwtAuthenticationToken,

        @PathVariable
        userId: UUID,

        @PathVariable
        roleCode: String,
    ) {
        roleService.removeRole(
            actorUserId =
                UUID.fromString(
                    authentication
                        .token
                        .subject
                ),

            targetUserId =
                userId,

            rawRoleCode =
                roleCode,
        )
    }
}
