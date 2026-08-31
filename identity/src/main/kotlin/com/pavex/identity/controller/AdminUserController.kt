package com.pavex.identity.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import com.pavex.identity.dto.admin.UpdateUserProfileRequest
import com.pavex.identity.dto.admin.UpdateUserStatusRequest
import com.pavex.identity.dto.admin.UserDetailResponse
import com.pavex.identity.dto.admin.UserListItemResponse
import com.pavex.common.api.PageResponse
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.ProfileStatus
import com.pavex.identity.security.AuthorizationPermissions
import com.pavex.identity.service.AdminUserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import com.pavex.identity.dto.admin.CreateUserRequest
import com.pavex.identity.dto.admin.CreateUserResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import java.net.URI
@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users", description = "Administrative user lifecycle and profile management")
class AdminUserController(

    private val adminUserService:
    AdminUserService,

){

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.READ_USERS}')"
    )
    @GetMapping
    fun search(

        @RequestParam(
            defaultValue = "1"
        )
        page: Int,

        @RequestParam(
            defaultValue = "20"
        )
        pageSize: Int,

        @RequestParam(
            required = false
        )
        search: String?,

        @RequestParam(
            required = false
        )
        role: String?,

        @RequestParam(
            required = false
        )
        status: AccountStatus?,

        @RequestParam(
            required = false
        )
        profileStatus:
            ProfileStatus?,

    ): PageResponse<UserListItemResponse> {

        return adminUserService
            .searchUsers(
                pageInput =
                    page,

                pageSizeInput =
                    pageSize,

                searchInput =
                    search,

                roleInput =
                    role,

                status =
                    status,

                profileStatus =
                    profileStatus,
            )
    }


    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.READ_USERS}')"
    )
    @GetMapping(
        "/{userId}"
    )
    fun detail(

        @PathVariable
        userId: UUID,

    ): UserDetailResponse {

        return adminUserService
            .getUser(
                userId
            )
    }


    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_USER_PROFILE}')"
    )
    @PutMapping(
        "/{userId}/profile"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    fun updateProfile(

        @PathVariable
        userId: UUID,

        @Valid
        @RequestBody
        request:
            UpdateUserProfileRequest,

    ) {

        adminUserService
            .updateProfile(
                userId =
                    userId,

                request =
                    request,
            )
    }


    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.MANAGE_USER_STATUS}')"
    )
    @PatchMapping(
        "/{userId}/status"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    fun updateStatus(

        @PathVariable
        userId: UUID,

        @Valid
        @RequestBody
        request:
            UpdateUserStatusRequest,

    ) {

        adminUserService
            .changeStatus(
                userId =
                    userId,

                newStatus =
                    request.status,
            )
    }
    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.CREATE_USERS}') " +
                "and " +
                "hasAuthority('${AuthorizationPermissions.Identity.ASSIGN_ROLE}')"
    )
    @PostMapping
    fun createUser(

        authentication:
        JwtAuthenticationToken,

        @Valid
        @RequestBody
        request:
        CreateUserRequest,

        ): ResponseEntity<CreateUserResponse> {

        val result =
            adminUserService
                .createUser(
                    actorUserId =
                        UUID.fromString(
                            authentication
                                .token
                                .subject
                        ),

                    request =
                        request,
                )

        return ResponseEntity
            .created(
                URI.create(
                    "/api/v1/users/${result.userId}"
                )
            )
            .body(
                result
            )
    }
    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Identity.INVITE_USERS}')"
    )
    @PostMapping(
        "/{userId}/invite"
    )
    @ResponseStatus(
        HttpStatus.NO_CONTENT
    )
    fun inviteUser(

        @PathVariable
        userId: UUID,

        ) {

        adminUserService.inviteUser(userId)
    }
}
