package com.pavex.auth.controller

import com.pavex.auth.audit.AuthAuditActions
import com.pavex.auth.audit.AuthAuditService
import com.pavex.auth.dto.auth.AcceptInviteRequest
import com.pavex.auth.dto.auth.AuthResponse
import com.pavex.auth.dto.auth.ChangePasswordRequest
import com.pavex.auth.dto.auth.ForgotPasswordRequest
import com.pavex.auth.dto.auth.GoogleLoginRequest
import com.pavex.auth.dto.auth.LoginRequest
import com.pavex.auth.dto.auth.LogoutRequest
import com.pavex.auth.dto.auth.MessageResponse
import com.pavex.auth.dto.auth.RefreshTokenRequest
import com.pavex.auth.dto.auth.RegisterRequest
import com.pavex.auth.dto.auth.RegisterResponse
import com.pavex.auth.dto.auth.ResendVerificationRequest
import com.pavex.auth.dto.auth.ResetPasswordRequest
import com.pavex.auth.dto.auth.VerifyEmailRequest
import com.pavex.auth.exception.ApiException
import com.pavex.auth.security.abuse.AuthRateLimitPolicy
import com.pavex.auth.security.abuse.AuthRateLimitService
import com.pavex.auth.service.AuthService
import com.pavex.auth.service.ChangePasswordService
import com.pavex.auth.service.ClientMetadata
import com.pavex.auth.service.EmailVerificationService
import com.pavex.auth.service.GoogleAuthService
import com.pavex.auth.service.InvitationService
import com.pavex.auth.service.PasswordResetService
import com.pavex.auth.service.SessionService
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

    private val authService: AuthService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val changePasswordService: ChangePasswordService,
    private val sessionService: SessionService,
    private val invitationService: InvitationService,
    private val googleAuthService: GoogleAuthService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid
        @RequestBody
        request: RegisterRequest,
        httpRequest: HttpServletRequest,
    ): RegisterResponse {
        val metadata = clientMetadata(httpRequest)
        val subject = normalizeEmail(request.email)

        return audited(
            action = AuthAuditActions.REGISTER,
            metadata = metadata,
            subject = subject,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.REGISTER_EMAIL,
                subject,
            )
            authService.register(request)
        }
    }

    @PostMapping("/login")
    fun login(
        @Valid
        @RequestBody
        request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse {
        val metadata = clientMetadata(httpRequest)
        val subject = normalizeEmail(request.email)

        return audited(
            action = AuthAuditActions.LOGIN,
            metadata = metadata,
            subject = subject,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.LOGIN_EMAIL,
                subject,
            )
            authService.login(
                request = request,
                clientMetadata = metadata,
            )
        }
    }

    @PostMapping("/refresh")
    fun refresh(
        @Valid
        @RequestBody
        request: RefreshTokenRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse {
        val metadata = clientMetadata(httpRequest)

        return audited(
            action = AuthAuditActions.REFRESH,
            metadata = metadata,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.REFRESH_TOKEN,
                request.refreshToken,
            )
            authService.refresh(
                request = request,
                clientMetadata = metadata,
            )
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid
        @RequestBody
        request: LogoutRequest,
        httpRequest: HttpServletRequest,
    ) {
        val metadata = clientMetadata(httpRequest)

        audited(
            action = AuthAuditActions.LOGOUT,
            metadata = metadata,
        ) {
            authService.logout(request)
        }
    }

    @PostMapping("/verify-email")
    fun verifyEmail(
        @RequestBody
        request: VerifyEmailRequest,
        httpRequest: HttpServletRequest,
    ): MessageResponse {
        val metadata = clientMetadata(httpRequest)

        return audited(
            action = AuthAuditActions.EMAIL_VERIFY,
            metadata = metadata,
            subject = request.token,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.VERIFY_EMAIL_TOKEN,
                request.token,
            )
            emailVerificationService.verify(request)

            MessageResponse(
                message = "Email verified successfully.",
            )
        }
    }

    @PostMapping("/resend-verification")
    fun resendVerification(
        @RequestBody
        request: ResendVerificationRequest,
        httpRequest: HttpServletRequest,
    ): MessageResponse {
        val metadata = clientMetadata(httpRequest)
        val subject = normalizeEmail(request.email)

        return audited(
            action = AuthAuditActions.EMAIL_RESEND,
            metadata = metadata,
            subject = subject,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.RESEND_EMAIL,
                subject,
            )
            emailVerificationService.resend(request)

            MessageResponse(
                message =
                    "If the account exists and is not verified, a verification email has been sent.",
            )
        }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid
        @RequestBody
        request: ForgotPasswordRequest,
        httpRequest: HttpServletRequest,
    ): MessageResponse {
        val metadata = clientMetadata(httpRequest)
        val subject = normalizeEmail(request.email)

        return audited(
            action = AuthAuditActions.PASSWORD_FORGOT,
            metadata = metadata,
            subject = subject,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.FORGOT_PASSWORD_EMAIL,
                subject,
            )
            passwordResetService.forgotPassword(request.email)

            MessageResponse(
                message =
                    "If the account exists, a password reset email has been sent.",
            )
        }
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid
        @RequestBody
        request: ResetPasswordRequest,
        httpRequest: HttpServletRequest,
    ): MessageResponse {
        val metadata = clientMetadata(httpRequest)

        return audited(
            action = AuthAuditActions.PASSWORD_RESET,
            metadata = metadata,
            subject = request.token,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.RESET_PASSWORD_TOKEN,
                request.token,
            )
            passwordResetService.resetPassword(
                rawTokenInput = request.token,
                newPassword = request.newPassword,
            )

            MessageResponse(
                message = "Password reset successfully.",
            )
        }
    }

    @PostMapping("/change-password")
    fun changePassword(
        authentication: JwtAuthenticationToken,
        @Valid
        @RequestBody
        request: ChangePasswordRequest,
        httpRequest: HttpServletRequest,
    ): MessageResponse {
        val userId = currentUserId(authentication)
        val metadata = clientMetadata(httpRequest)

        return audited(
            action = AuthAuditActions.PASSWORD_CHANGE,
            metadata = metadata,
            actorUserId = userId,
        ) {
            changePasswordService.changePassword(
                userId = userId,
                currentPassword = request.currentPassword,
                newPassword = request.newPassword,
            )

            MessageResponse(
                message = "Password changed successfully.",
            )
        }
    }

    @GetMapping("/sessions")
    fun sessions(
        authentication: JwtAuthenticationToken,
    ) =
        sessionService.getSessions(
            userId = currentUserId(authentication),
        )

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeSession(
        authentication: JwtAuthenticationToken,
        @PathVariable
        sessionId: UUID,
        httpRequest: HttpServletRequest,
    ) {
        val userId = currentUserId(authentication)
        val metadata = clientMetadata(httpRequest)

        audited(
            action = AuthAuditActions.SESSION_REVOKE,
            metadata = metadata,
            actorUserId = userId,
        ) {
            sessionService.revokeSession(
                userId = userId,
                sessionId = sessionId,
            )
        }
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logoutAll(
        authentication: JwtAuthenticationToken,
        httpRequest: HttpServletRequest,
    ) {
        val userId = currentUserId(authentication)
        val metadata = clientMetadata(httpRequest)

        audited(
            action = AuthAuditActions.LOGOUT_ALL,
            metadata = metadata,
            actorUserId = userId,
        ) {
            sessionService.logoutAll(userId)
        }
    }

    @PostMapping("/accept-invite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptInvite(
        @Valid
        @RequestBody
        request: AcceptInviteRequest,
        httpRequest: HttpServletRequest,
    ) {
        val metadata = clientMetadata(httpRequest)

        audited(
            action = AuthAuditActions.INVITATION_ACCEPT,
            metadata = metadata,
            subject = request.token,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.ACCEPT_INVITE_TOKEN,
                request.token,
            )
            invitationService.acceptInvite(
                rawTokenInput = request.token,
                password = request.password,
            )
        }
    }

    @PostMapping("/google")
    fun googleLogin(
        @Valid
        @RequestBody
        request: GoogleLoginRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse {
        val metadata = clientMetadata(httpRequest)

        return audited(
            action = AuthAuditActions.GOOGLE_LOGIN,
            metadata = metadata,
        ) {
            authRateLimitService.check(
                AuthRateLimitPolicy.GOOGLE_TOKEN,
                request.idToken,
            )
            googleAuthService.login(
                rawIdToken = request.idToken,
                clientMetadata = metadata,
            )
        }
    }

    private fun <T> audited(
        action: String,
        metadata: ClientMetadata,
        actorUserId: UUID? = null,
        subject: String? = null,
        block: () -> T,
    ): T {
        return try {
            val result = block()

            authAuditService.success(
                action = action,
                metadata = metadata,
                actorUserId = actorUserId,
                subject = subject,
            )

            result
        } catch (exception: ApiException) {
            authAuditService.failure(
                action = action,
                reasonCode = exception.code,
                metadata = metadata,
                actorUserId = actorUserId,
                subject = subject,
            )

            throw exception
        }
    }

    private fun currentUserId(
        authentication: JwtAuthenticationToken,
    ): UUID =
        UUID.fromString(
            authentication.token.subject
        )

    private fun clientMetadata(
        request: HttpServletRequest,
    ): ClientMetadata =
        ClientMetadata(
            ipAddress =
                request.remoteAddr
                    ?.take(64),
            userAgent =
                request
                    .getHeader("User-Agent")
                    ?.take(512),
            requestId =
                request
                    .getHeader(REQUEST_ID_HEADER)
                    ?.take(128),
        )

    private fun normalizeEmail(
        email: String,
    ): String =
        email
            .trim()
            .lowercase()

    companion object {
        private const val REQUEST_ID_HEADER = "X-Request-Id"
    }
}
