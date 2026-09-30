package com.pavex.auth.service

import com.pavex.auth.dto.auth.AuthResponse
import com.pavex.auth.dto.auth.LoginRequest
import com.pavex.auth.dto.auth.LogoutRequest
import com.pavex.auth.dto.auth.RefreshTokenRequest
import com.pavex.auth.dto.auth.RegisterRequest
import com.pavex.auth.dto.auth.RegisterResponse
import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.AccountLockedException
import com.pavex.auth.exception.EmailAlreadyExistsException
import com.pavex.auth.exception.EmailNotVerifiedException
import com.pavex.auth.exception.TemporaryPasswordExpiredException
import com.pavex.auth.exception.InvalidCredentialsException
import com.pavex.auth.exception.InvalidRefreshTokenException
import com.pavex.auth.exception.RefreshTokenReuseDetectedException
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class AuthService(

    private val userAccountRepository:
        UserAccountRepository,

    private val passwordEncoder:
        PasswordEncoder,

    private val jwtService:
        JwtService,

    private val authSessionService:
        AuthSessionService,

    private val refreshTokenService:
        RefreshTokenService,

    private val emailVerificationService:
        EmailVerificationService,

    private val advisoryLockService:
        AdvisoryLockService,
) {

    @Transactional
    fun register(
        request: RegisterRequest,
    ): RegisterResponse {

        val email =
            normalizeEmail(
                request.email
            )

        /*
         * Serialize register/admin-create/Google-first-link
         * theo normalized email để tránh check-then-insert race.
         */
        advisoryLockService
            .lockEmail(
                email
            )

        if (
            userAccountRepository
                .existsByEmail(
                    email
                )
        ) {
            throw EmailAlreadyExistsException(
                email
            )
        }

        val now =
            Instant.now()

        val encodedPassword =
            requireNotNull(
                passwordEncoder.encode(
                    request.password
                )
            ) {
                "Password encoder returned null"
            }

        val account =
            UserAccount(
                email = email,
                passwordHash =
                    encodedPassword,
                createdAt = now,
                updatedAt = now,
            )

        val savedAccount =
            userAccountRepository.save(
                account
            )

        emailVerificationService
            .issueForRegistration(
                userId =
                    savedAccount.id,
                email =
                    savedAccount.email,
                now =
                    now,
            )

        return RegisterResponse(
            id =
                savedAccount.id,
            email =
                savedAccount.email,
            status =
                DEFAULT_IDENTITY_STATUS,
            emailVerified =
                savedAccount.emailVerified,
            emailVerificationRequired =
                !savedAccount.emailVerified,
            createdAt =
                savedAccount.createdAt,
        )
    }


    @Transactional(
        noRollbackFor = [
            InvalidCredentialsException::class,
        ],
    )
    fun login(
        request: LoginRequest,
        clientMetadata: ClientMetadata,
    ): AuthResponse {

        val email =
            normalizeEmail(
                request.email
            )

        val account =
            userAccountRepository
                .findByEmail(
                    email
                )
                ?: throw InvalidCredentialsException()

        val now =
            Instant.now()

        val lockoutEnd =
            account.lockoutEndAt

        if (
            lockoutEnd != null &&
            lockoutEnd.isAfter(now)
        ) {
            throw AccountLockedException(
                lockoutEnd
            )
        }

        if (
            lockoutEnd != null &&
            !lockoutEnd.isAfter(now)
        ) {
            account.failedLoginAttempts = 0
            account.lockoutEndAt = null
        }

        val matches =
            passwordEncoder.matches(
                request.password,
                account.passwordHash,
            )

        if (!matches) {

            account.failedLoginAttempts += 1
            account.updatedAt = now

            if (
                account.failedLoginAttempts >=
                MAX_FAILED_LOGIN_ATTEMPTS
            ) {
                account.lockoutEndAt =
                    now.plus(
                        LOGIN_LOCKOUT_DURATION
                    )
            }

            userAccountRepository.save(
                account
            )

            throw InvalidCredentialsException()
        }

        if (!account.emailVerified) {
            throw EmailNotVerifiedException()
        }

        if (account.mustChangePassword && account.temporaryPasswordExpiresAt?.isBefore(now) == true) {
            throw TemporaryPasswordExpiredException()
        }

        // Authenticate the credential first. An invalid password must not
        // provision/repair Identity state or depend on Identity availability.
        val authorization = jwtService.resolveAuthorization(account)

        account.failedLoginAttempts = 0
        account.lockoutEndAt = null
        account.lastLoginAt = now
        account.updatedAt = now

        userAccountRepository.save(
            account
        )

        return createAuthResponse(
            account = account,
            now = now,
            clientMetadata = clientMetadata,
            authorization = authorization,
        )
    }


    @Transactional(
        noRollbackFor = [
            RefreshTokenReuseDetectedException::class,
        ],
    )
    fun refresh(
        request: RefreshTokenRequest,
        clientMetadata: ClientMetadata,
    ): AuthResponse {

        val now =
            Instant.now()

        val rotated =
            refreshTokenService.rotate(
                rawToken =
                    request.refreshToken,
                now =
                    now,
                clientMetadata =
                    clientMetadata,
            )

        val account =
            userAccountRepository
                .findById(
                    rotated.userId
                )
                .orElseThrow {
                    InvalidRefreshTokenException()
                }

        val authorization =
            jwtService.resolveAuthorization(
                account
            )

        val accessToken =
            jwtService.createAccessToken(
                account = account,
                now = now,
                authorization = authorization,
            )

        return AuthResponse(
            accessToken =
                accessToken.token,
            refreshToken =
                rotated.token,
            expiresIn =
                accessToken.expiresIn,
        )
    }


    @Transactional
    fun logout(
        request: LogoutRequest,
    ) {

        refreshTokenService.revoke(
            rawToken =
                request.refreshToken,
            now =
                Instant.now(),
        )
    }


    private fun createAuthResponse(
        account: UserAccount,
        now: Instant,
        clientMetadata: ClientMetadata,
        authorization: com.pavex.contracts.identity.IdentityAuthContext,
    ): AuthResponse {

        return authSessionService
            .issue(
                account =
                    account,
                now =
                    now,
                clientMetadata =
                    clientMetadata,
                authorization =
                    authorization,
            )
    }


    private fun normalizeEmail(
        email: String,
    ): String {

        return email
            .trim()
            .lowercase()
    }


    companion object {

        private const val
            DEFAULT_IDENTITY_STATUS = "ACTIVE"

        private const val
            MAX_FAILED_LOGIN_ATTEMPTS = 5

        private val
            LOGIN_LOCKOUT_DURATION =
                Duration.ofMinutes(15)
    }
}
