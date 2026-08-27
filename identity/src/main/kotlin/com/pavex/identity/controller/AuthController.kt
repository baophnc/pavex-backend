package com.pavex.identity.controller

import com.pavex.identity.dto.auth.AuthResponse
import com.pavex.identity.dto.auth.LoginRequest
import com.pavex.identity.dto.auth.LogoutRequest
import com.pavex.identity.dto.auth.RefreshTokenRequest
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
import com.pavex.identity.dto.auth.MessageResponse
import com.pavex.identity.dto.auth.ResendVerificationRequest
import com.pavex.identity.dto.auth.VerifyEmailRequest
import com.pavex.identity.service.EmailVerificationService
import com.pavex.identity.dto.auth.ForgotPasswordRequest
import com.pavex.identity.dto.auth.ResetPasswordRequest
import com.pavex.identity.service.PasswordResetService
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val emailVerificationService:
    EmailVerificationService,
    private val passwordResetService:
    PasswordResetService,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid
        @RequestBody
        request: RegisterRequest,
    ): RegisterResponse {

        return authService.register(
            request
        )
    }


    @PostMapping("/login")
    fun login(
        @Valid
        @RequestBody
        request: LoginRequest,
    ): AuthResponse {

        return authService.login(
            request
        )
    }


    @PostMapping("/refresh")
    fun refresh(
        @Valid
        @RequestBody
        request: RefreshTokenRequest,
    ): AuthResponse {

        return authService.refresh(
            request
        )
    }


    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid
        @RequestBody
        request: LogoutRequest,
    ) {

        authService.logout(
            request
        )
    }
    @PostMapping("/verify-email")
    fun verifyEmail(
        @RequestBody
        request: VerifyEmailRequest,
    ): MessageResponse {

        emailVerificationService
            .verify(
                request
            )

        return MessageResponse(
            message =
                "Email verified successfully."
        )
    }


    @PostMapping("/resend-verification")
    fun resendVerification(
        @RequestBody
        request: ResendVerificationRequest,
    ): MessageResponse {

        emailVerificationService
            .resend(
                request
            )

        return MessageResponse(
            message =
                "If the account exists and is not verified, a verification email has been sent."
        )
    }
    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid
        @RequestBody
        request: ForgotPasswordRequest,
    ): MessageResponse {

        passwordResetService
            .forgotPassword(
                request.email
            )

        return MessageResponse(
            message =
                "If the account exists, a password reset email has been sent.",
        )
    }


    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid
        @RequestBody
        request: ResetPasswordRequest,
    ): MessageResponse {

        passwordResetService
            .resetPassword(
                rawTokenInput =
                    request.token,
                newPassword =
                    request.newPassword,
            )

        return MessageResponse(
            message =
                "Password reset successfully.",
        )
    }
}