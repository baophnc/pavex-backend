package com.pavex.identity.service

import com.pavex.identity.dto.auth.AuthResponse
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.ExternalIdentity
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.AccountDisabledException
import com.pavex.identity.exception.AccountSuspendedException
import com.pavex.identity.exception.ExternalIdentityLinkRequiredException
import com.pavex.identity.exception.GoogleEmailNotAuthoritativeException
import com.pavex.identity.exception.InvalidGoogleTokenException
import com.pavex.identity.repository.ExternalIdentityRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.security.OpaqueTokenService
import com.pavex.identity.security.google.GoogleIdTokenVerifier
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

    private val userProfileRepository:
        UserProfileRepository,

    private val roleService:
        RoleService,

    private val passwordEncoder:
        PasswordEncoder,

    private val opaqueTokenService:
        OpaqueTokenService,

    private val authSessionService:
        AuthSessionService,

    private val advisoryLockService:
        AdvisoryLockService,
) {

    @Transactional
    fun login(

        rawIdToken: String,

        clientMetadata:
            ClientMetadata,

    ): AuthResponse {

        val googleUser =
            googleIdTokenVerifier
                .verify(
                    rawIdToken
                )

        advisoryLockService
            .lockGoogleIdentity(
                subject =
                    googleUser.subject,
                email =
                    googleUser.email,
            )

        val now =
            Instant.now()

        /*
         * Sau advisory lock mới query DB.
         * Hai first-login song song cùng Google sub/email
         * không thể cùng create account/external_identity.
         *
         * Identity chính là:
         *
         * GOOGLE + sub
         *
         * KHÔNG phải email.
         */
        val linkedIdentity =
            externalIdentityRepository
                .findByProviderAndProviderUserId(
                    provider =
                        GOOGLE_PROVIDER,

                    providerUserId =
                        googleUser.subject,
                )

        val account =
            if (linkedIdentity != null) {

                val linkedAccount =
                    userAccountRepository
                        .findById(
                            linkedIdentity.userId
                        )
                        .orElseThrow {
                            InvalidGoogleTokenException()
                        }

                requireActive(
                    linkedAccount
                )

                linkedAccount

            } else {

                resolveOrCreateAccount(
                    email =
                        googleUser.email,

                    emailAuthoritative =
                        googleUser.emailAuthoritative,

                    displayName =
                        googleUser.displayName,

                    avatarUrl =
                        googleUser.avatarUrl,

                    googleSubject =
                        googleUser.subject,

                    now =
                        now,
                )
            }

        account.lastLoginAt =
            now

        account.failedLoginAttempts =
            0

        account.lockoutEndAt =
            null

        account.updatedAt =
            now

        userAccountRepository.save(
            account
        )

        return authSessionService
            .issue(
                account =
                    account,

                now =
                    now,

                clientMetadata =
                    clientMetadata,
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
            userAccountRepository
                .findByEmail(
                    email
                )

        /*
         * PAVEX account đã tồn tại.
         */
        if (existingAccount != null) {

            requireActive(
                existingAccount
            )

            /*
             * Improvement:
             *
             * Không bypass invitation /
             * email-verification flow.
             */
            if (!existingAccount.emailVerified) {
                throw ExternalIdentityLinkRequiredException()
            }

            /*
             * Không auto-link email mà Google
             * không authoritative.
             */
            if (!emailAuthoritative) {
                throw ExternalIdentityLinkRequiredException()
            }

            createExternalIdentity(
                account =
                    existingAccount,

                googleSubject =
                    googleSubject,

                email =
                    email,

                now =
                    now,
            )

            return existingAccount
        }

        /*
         * Account hoàn toàn mới.
         *
         * Không tạo PAVEX user từ một Google
         * account mà Google không authoritative
         * cho email đó.
         */
        if (!emailAuthoritative) {
            throw GoogleEmailNotAuthoritativeException()
        }

        /*
         * Password nội bộ random.
         *
         * Không ai biết password này.
         * User Google không tự nhiên có
         * password login PAVEX.
         */
        val internalSecret =
            opaqueTokenService
                .issue()
                .raw

        val passwordHash =
            requireNotNull(
                passwordEncoder
                    .encode(
                        internalSecret
                    )
            ) {
                "Password encoder returned null"
            }

        val account =
            UserAccount(
                email =
                    email,

                passwordHash =
                    passwordHash,

                emailVerified =
                    true,

                createdAt =
                    now,

                updatedAt =
                    now,
            )

        val savedAccount =
            userAccountRepository
                .save(
                    account
                )

        userProfileRepository.save(
            UserProfile(
                userId =
                    savedAccount.id,

                displayName =
                    displayName
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        },

                avatarUrl =
                    avatarUrl
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        },

                createdAt =
                    now,

                updatedAt =
                    now,
            )
        )

        roleService
            .assignDefaultCustomerRole(
                userId =
                    savedAccount.id,

                now =
                    now,
            )

        createExternalIdentity(
            account =
                savedAccount,

            googleSubject =
                googleSubject,

            email =
                email,

            now =
                now,
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
                userId =
                    account.id,

                provider =
                    GOOGLE_PROVIDER,

                providerUserId =
                    googleSubject,

                email =
                    email,

                createdAt =
                    now,
            )
        )
    }


    private fun requireActive(
        account: UserAccount,
    ) {

        when (account.status) {

            AccountStatus.ACTIVE ->
                Unit

            AccountStatus.SUSPENDED ->
                throw AccountSuspendedException()

            AccountStatus.DISABLED ->
                throw AccountDisabledException()
        }
    }


    companion object {

        private const val
            GOOGLE_PROVIDER =
                "GOOGLE"
    }
}
