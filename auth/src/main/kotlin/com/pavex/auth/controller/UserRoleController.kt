package com.pavex.auth.controller

import com.pavex.auth.dto.role.AssignRoleRequest
import com.pavex.auth.security.AuthorizationPermissions
import com.pavex.auth.service.RoleService
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
class UserRoleController(

    private val roleService:
        RoleService,
) {

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Auth.ASSIGN_ROLE}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.ASSIGN_ROLE}')"
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
