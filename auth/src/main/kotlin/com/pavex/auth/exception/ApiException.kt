package com.pavex.auth.exception

import org.springframework.http.HttpStatus
import java.time.Instant

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)


class EmailAlreadyExistsException(
    email: String,
) : ApiException(
    status = HttpStatus.CONFLICT,
    code = "email_already_exists",
    message = "An account with email '$email' already exists",
)


class InvalidCredentialsException :
    ApiException(
        status = HttpStatus.UNAUTHORIZED,
        code = "invalid_credentials",
        message = "Email or password is incorrect",
    )


class AccountLockedException(
    lockedUntil: Instant,
) : ApiException(
    status = HttpStatus.TOO_MANY_REQUESTS,
    code = "account_locked",
    message = "Account is temporarily locked until $lockedUntil",
)


class AccountSuspendedException :
    ApiException(
        status = HttpStatus.FORBIDDEN,
        code = "account_suspended",
        message = "Account is suspended",
    )


class AccountDisabledException :
    ApiException(
        status = HttpStatus.FORBIDDEN,
        code = "account_disabled",
        message = "Account is disabled",
    )


class InvalidRefreshTokenException :
    ApiException(
        status = HttpStatus.UNAUTHORIZED,
        code = "invalid_refresh_token",
        message = "Refresh token is invalid or expired",
    )
class InvalidEmailVerificationTokenException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid_email_verification_token",
        message = "Invalid or expired verification token.",
    )

class IdentityServiceUnavailableException :
    ApiException(
        status = HttpStatus.SERVICE_UNAVAILABLE,
        code = "identity_service_unavailable",
        message = "Identity service is temporarily unavailable",
    )

class CredentialProvisionConflictException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "credential_provision_conflict",
        message = "Credential identity does not match the requested user",
    )
