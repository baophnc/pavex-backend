package com.pavex.auth.controller

import com.pavex.auth.dto.user.CurrentUserResponse
import com.pavex.auth.dto.user.UpdateProfileRequest
import com.pavex.auth.service.UserService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class UserController(

    private val userService:
    UserService,
) {

    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal
        jwt: Jwt,
    ): CurrentUserResponse {

        return userService
            .getCurrentUser(
                getUserId(jwt)
            )
    }


    @PatchMapping("/me/profile")
    fun updateProfile(
        @AuthenticationPrincipal
        jwt: Jwt,

        @Valid
        @RequestBody
        request: UpdateProfileRequest,
    ): CurrentUserResponse {

        return userService
            .updateProfile(
                userId =
                    getUserId(jwt),

                request =
                    request,
            )
    }


    private fun getUserId(
        jwt: Jwt,
    ): UUID {

        return UUID.fromString(
            jwt.subject
        )
    }
}