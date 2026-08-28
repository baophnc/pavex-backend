package com.pavex.auth.controller

import com.pavex.auth.dto.admin.UpdateUserProfileRequest
import com.pavex.auth.dto.admin.UpdateUserStatusRequest
import com.pavex.auth.dto.admin.UserDetailResponse
import com.pavex.auth.dto.admin.UserListItemResponse
import com.pavex.auth.dto.common.PageResponse
import com.pavex.auth.entity.AccountStatus
import com.pavex.auth.entity.ProfileStatus
import com.pavex.auth.security.AuthorizationPermissions
import com.pavex.auth.service.AdminUserService
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
import com.pavex.auth.dto.admin.CreateUserRequest
import com.pavex.auth.dto.admin.CreateUserResponse
import com.pavex.auth.service.InvitationService
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import java.net.URI
@RestController
@RequestMapping("/api/v1/users")
class AdminUserController(

    private val adminUserService:
    AdminUserService,

    private val invitationService:
    InvitationService,
){

    @PreAuthorize(
        "hasAuthority('${AuthorizationPermissions.Auth.READ_USERS}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.READ_USERS}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.MANAGE_USER_PROFILE}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.MANAGE_USER_STATUS}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.CREATE_USERS}') " +
                "and " +
                "hasAuthority('${AuthorizationPermissions.Auth.ASSIGN_ROLE}')"
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
        "hasAuthority('${AuthorizationPermissions.Auth.INVITE_USERS}')"
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

        invitationService
            .sendInvite(
                userId
            )
    }
}
