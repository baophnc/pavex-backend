package com.pavex.identity.controller

import com.pavex.identity.dto.auth.AcceptInviteRequest
import com.pavex.identity.dto.auth.AuthResponse
import com.pavex.identity.dto.auth.ChangePasswordRequest
import com.pavex.identity.dto.auth.ForgotPasswordRequest
import com.pavex.identity.dto.auth.GoogleLoginRequest
import com.pavex.identity.dto.auth.LoginRequest
import com.pavex.identity.dto.auth.LogoutRequest
import com.pavex.identity.dto.auth.MessageResponse
import com.pavex.identity.dto.auth.RefreshTokenRequest
import com.pavex.identity.dto.auth.RegisterRequest
import com.pavex.identity.dto.auth.RegisterResponse
import com.pavex.identity.dto.auth.ResendVerificationRequest
import com.pavex.identity.dto.auth.ResetPasswordRequest
import com.pavex.identity.dto.auth.VerifyEmailRequest
import com.pavex.identity.service.AuthService
import com.pavex.identity.service.ChangePasswordService
import com.pavex.identity.service.ClientMetadata
import com.pavex.identity.service.EmailVerificationService
import com.pavex.identity.service.GoogleAuthService
import com.pavex.identity.service.InvitationService
import com.pavex.identity.service.PasswordResetService
import com.pavex.identity.service.SessionService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(

    private val authService:
        AuthService,

    private val emailVerificationService:
        EmailVerificationService,

    private val passwordResetService:
        PasswordResetService,

    private val changePasswordService:
        ChangePasswordService,

    private val sessionService:
        SessionService,

    private val invitationService:
        InvitationService,

    private val googleAuthService:
        GoogleAuthService,
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
        httpRequest: HttpServletRequest,
    ): AuthResponse {

        return authService.login(
            request = request,
            clientMetadata =
                clientMetadata(
                    httpRequest
                ),
        )
    }


    @PostMapping("/refresh")
    fun refresh(
        @Valid
        @RequestBody
        request: RefreshTokenRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse {

        return authService.refresh(
            request = request,
            clientMetadata =
                clientMetadata(
                    httpRequest
                ),
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

        emailVerificationService.verify(
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

        emailVerificationService.resend(
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

        passwordResetService.forgotPassword(
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

        passwordResetService.resetPassword(
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


    @PostMapping("/change-password")
    fun changePassword(
        authentication: JwtAuthenticationToken,
        @Valid
        @RequestBody
        request: ChangePasswordRequest,
    ): MessageResponse {

        changePasswordService.changePassword(
            userId =
                currentUserId(
                    authentication
                ),
            currentPassword =
                request.currentPassword,
            newPassword =
                request.newPassword,
        )

        return MessageResponse(
            message =
                "Password changed successfully.",
        )
    }


    @GetMapping("/sessions")
    fun sessions(
        authentication: JwtAuthenticationToken,
    ) =
        sessionService.getSessions(
            userId =
                currentUserId(
                    authentication
                )
        )


    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSession(
        authentication: JwtAuthenticationToken,
        @PathVariable
        sessionId: UUID,
    ) {

        sessionService.revokeSession(
            userId =
                currentUserId(
                    authentication
                ),
            sessionId =
                sessionId,
        )
    }


    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(
        authentication: JwtAuthenticationToken,
    ) {

        sessionService.logoutAll(
            userId =
                currentUserId(
                    authentication
                )
        )
    }


    @PostMapping("/accept-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptInvite(
        @Valid
        @RequestBody
        request: AcceptInviteRequest,
    ) {

        invitationService.acceptInvite(
            rawTokenInput =
                request.token,
            password =
                request.password,
        )
    }


    @PostMapping("/google")
    fun googleLogin(
        @Valid
        @RequestBody
        request: GoogleLoginRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse {

        return googleAuthService.login(
            rawIdToken =
                request.idToken,
            clientMetadata =
                clientMetadata(
                    httpRequest
                ),
        )
    }


    private fun currentUserId(
        authentication: JwtAuthenticationToken,
    ): UUID {

        return UUID.fromString(
            authentication.token.subject
        )
    }


    private fun clientMetadata(
        request: HttpServletRequest,
    ): ClientMetadata {

        return ClientMetadata(
            ipAddress =
                request.remoteAddr
                    ?.take(64),
            userAgent =
                request
                    .getHeader(
                        "User-Agent"
                    )
                    ?.take(512),
        )
    }
}
