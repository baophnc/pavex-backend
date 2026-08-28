package com.pavex.auth.service

import com.pavex.auth.dto.auth.AuthResponse
import com.pavex.auth.entity.ExternalIdentity
import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.ExternalIdentityLinkRequiredException
import com.pavex.auth.exception.GoogleEmailNotAuthoritativeException
import com.pavex.auth.exception.InvalidGoogleTokenException
import com.pavex.auth.repository.ExternalIdentityRepository
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.JwtService
import com.pavex.auth.security.OpaqueTokenService
import com.pavex.auth.security.google.GoogleIdTokenVerifier
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class GoogleAuthService(

    private val googleIdTokenVerifier:
        GoogleIdTokenVerifier,

    private val externalIdentityRepository:
        ExternalIdentityRepository,

    private val userAccountRepository:
        UserAccountRepository,

    private val passwordEncoder:
        PasswordEncoder,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val authSessionService:
        AuthSessionService,

    private val jwtService:
        JwtService,

    private val identityClient:
        IdentityClient,

    private val advisoryLockService:
        AdvisoryLockService,
) {

    @Transactional
    fun login(
        rawIdToken: String,
        clientMetadata: ClientMetadata,
    ): AuthResponse {

        val googleUser =
            googleIdTokenVerifier
                .verify(rawIdToken)

        advisoryLockService.lockGoogleIdentity(
            subject = googleUser.subject,
            email = googleUser.email,
        )

        val now = Instant.now()

        val linkedIdentity =
            externalIdentityRepository
                .findByProviderAndProviderUserId(
                    provider = GOOGLE_PROVIDER,
                    providerUserId = googleUser.subject,
                )

        val account =
            if (linkedIdentity != null) {
                userAccountRepository
                    .findById(linkedIdentity.userId)
                    .orElseThrow {
                        InvalidGoogleTokenException()
                    }
            } else {
                resolveOrCreateAccount(
                    email = googleUser.email,
                    emailAuthoritative = googleUser.emailAuthoritative,
                    displayName = googleUser.displayName,
                    avatarUrl = googleUser.avatarUrl,
                    googleSubject = googleUser.subject,
                    now = now,
                )
            }

        /*
         * Identity owns ACTIVE/SUSPENDED/DISABLED and authorization. Resolving
         * the context here makes Google login fail closed before a session is
         * issued and also repairs a missed email-verification synchronization.
         */
        val authorization =
            jwtService.resolveAuthorization(account)

        account.lastLoginAt = now
        account.failedLoginAttempts = 0
        account.lockoutEndAt = null
        account.updatedAt = now

        userAccountRepository.save(account)

        return authSessionService.issue(
            account = account,
            now = now,
            clientMetadata = clientMetadata,
            authorization = authorization,
        )
    }

    private fun resolveOrCreateAccount(
        email: String,
        emailAuthoritative: Boolean,
        displayName: String?,
        avatarUrl: String?,
        googleSubject: String,
        now: Instant,
    ): UserAccount {

        val existingAccount =
            userAccountRepository.findByEmail(email)

        if (existingAccount != null) {
            /*
             * Auth still owns the verification credential flow during this
             * extraction checkpoint. The mirror is synchronized to Identity
             * after verification and self-healed before token issuance.
             */
            if (!existingAccount.emailVerified) {
                throw ExternalIdentityLinkRequiredException()
            }

            if (!emailAuthoritative) {
                throw ExternalIdentityLinkRequiredException()
            }

            createExternalIdentity(
                account = existingAccount,
                googleSubject = googleSubject,
                email = email,
                now = now,
            )

            return existingAccount
        }

        if (!emailAuthoritative) {
            throw GoogleEmailNotAuthoritativeException()
        }

        val internalSecret =
            opaqueTokenService.issue().raw

        val passwordHash =
            requireNotNull(
                passwordEncoder.encode(internalSecret)
            ) {
                "Password encoder returned null"
            }

        val savedAccount =
            userAccountRepository.save(
                UserAccount(
                    email = email,
                    passwordHash = passwordHash,
                    emailVerified = true,
                    createdAt = now,
                    updatedAt = now,
                )
            )

        /*
         * New Google users are provisioned directly into the Identity-owned
         * user/RBAC/profile model. Auth no longer writes user_profile or role
         * assignments for new accounts.
         */
        identityClient.provision(
            account = savedAccount,
            roles = setOf(DEFAULT_CUSTOMER_ROLE),
            displayName = displayName,
            avatarUrl = avatarUrl,
        )
        savedAccount.identityProvisioned = true
        savedAccount.updatedAt = now
        userAccountRepository.save(savedAccount)

        createExternalIdentity(
            account = savedAccount,
            googleSubject = googleSubject,
            email = email,
            now = now,
        )

        return savedAccount
    }

    private fun createExternalIdentity(
        account: UserAccount,
        googleSubject: String,
        email: String,
        now: Instant,
    ) {
        externalIdentityRepository.save(
            ExternalIdentity(
                userId = account.id,
                provider = GOOGLE_PROVIDER,
                providerUserId = googleSubject,
                email = email,
                createdAt = now,
            )
        )
    }

    companion object {
        private const val GOOGLE_PROVIDER = "GOOGLE"
        private const val DEFAULT_CUSTOMER_ROLE = "CUSTOMER"
    }
}
