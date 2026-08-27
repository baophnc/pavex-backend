package com.pavex.identity.controller

import com.pavex.identity.dto.auth.RegisterRequest
import com.pavex.identity.dto.auth.RegisterResponse
import com.pavex.identity.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid
        @RequestBody
        request: RegisterRequest,
    ): RegisterResponse {

        return authService.register(
            request,
        )
    }
}