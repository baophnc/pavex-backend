package com.pavex.auth.service

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.event.PasswordResetPortal
import com.pavex.auth.exception.CredentialProvisionConflictException
import com.pavex.auth.exception.EmailAlreadyExistsException
import com.pavex.auth.exception.UserNotFoundException
import com.pavex.auth.repository.ExternalIdentityRepository
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import com.pavex.contracts.identity.CredentialEmailRequest
import com.pavex.contracts.identity.CredentialInvitationRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InternalCredentialService(
    private val accounts: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val opaqueTokenService: OpaqueTokenService,
    private val invitationService: InvitationService,
    private val sessionService: SessionService,
    private val advisoryLockService: AdvisoryLockService,
    private val externalIdentities: ExternalIdentityRepository,
    private val emailVerification: EmailVerificationService,
    @Value("\${auth.staff-credentials.temporary-password}")
    private val temporaryPassword: String,
    @Value("\${auth.staff-credentials.temporary-password-ttl}")
    private val temporaryPasswordTtl: Duration,
) {

    /**
     * Creates the Auth-side credential shell for a user that is owned by
     * Identity. The operation is deliberately idempotent because Identity may
     * retry the internal request after a network failure.
     */
    @Transactional
    fun provisionInvitation(request: CredentialInvitationRequest) {
        val email = normalizeEmail(request.email)

        advisoryLockService.lockEmail(email)

        val byId = accounts.findById(request.userId).orElse(null)
        if (byId != null) {
            if (!byId.email.equals(email, ignoreCase = true)) {
                throw CredentialProvisionConflictException()
            }
            if (!byId.identityProvisioned) {
                byId.identityProvisioned = true
                byId.updatedAt = Instant.now()
                accounts.save(byId)
            }
            return
        }

        val byEmail = accounts.findByEmail(email)
        if (byEmail != null && byEmail.id != request.userId) {
            throw CredentialProvisionConflictException()
        }

        // Staff an administrator creates sign in with the temporary password (the email was given
        // to them by the company, so it counts as verified) and must choose their own at once.
        val now = Instant.now()
        val passwordHash = requireNotNull(passwordEncoder.encode(temporaryPassword)) {
            "Password encoder returned null"
        }

        accounts.save(
            UserAccount(
                id = request.userId,
                email = email,
                passwordHash = passwordHash,
                emailVerified = true,
                identityProvisioned = true,
                mustChangePassword = true,
                temporaryPasswordExpiresAt = now.plus(temporaryPasswordTtl),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    @Transactional
    fun sendInvitation(request: CredentialInvitationRequest) {
        ensureCredential(request)
        invitationService.sendInvite(
            request.userId,
            if (request.staff) PasswordResetPortal.MANAGEMENT else PasswordResetPortal.CLIENT,
        )
    }

    @Transactional
    fun revokeSessions(userId: UUID) {
        sessionService.logoutAll(userId)
    }

    /**
     * The account was deleted in Identity: its email becomes the placeholder Identity chose (so the real
     * address can sign up again), the password and Google link stop working, and every session ends.
     */
    @Transactional
    fun close(userId: UUID, request: CredentialEmailRequest) {
        val account = accounts.findById(userId).orElse(null) ?: return
        val placeholder = normalizeEmail(request.email)
        advisoryLockService.lockEmail(placeholder)
        account.email = placeholder
        account.emailVerified = false
        account.passwordHash = requireNotNull(passwordEncoder.encode(opaqueTokenService.issue().raw)) {
            "Password encoder returned null"
        }
        account.mustChangePassword = false
        account.temporaryPasswordExpiresAt = null
        account.updatedAt = Instant.now()
        accounts.save(account)
        externalIdentities.deleteAllByUserId(userId)
        sessionService.logoutAll(userId)
    }

    /**
     * An administrator changed the account's email. It must be free, signs every session out, and is
     * confirmed again through a verification email sent to the new address.
     */
    @Transactional
    fun changeEmail(userId: UUID, request: CredentialEmailRequest) {
        val email = normalizeEmail(request.email)
        advisoryLockService.lockEmail(email)
        val account = accounts.findById(userId).orElseThrow { UserNotFoundException() }
        if (account.email == email) return
        val taken = accounts.findByEmail(email)
        if (taken != null && taken.id != userId) throw EmailAlreadyExistsException(email)
        account.email = email
        account.emailVerified = false
        account.updatedAt = Instant.now()
        accounts.save(account)
        // A Google link was made for the old address; the new one signs in with a password or relinks.
        externalIdentities.deleteAllByUserId(userId)
        sessionService.logoutAll(userId)
        emailVerification.issueForChangedEmail(userId, email)
    }

    private fun ensureCredential(request: CredentialInvitationRequest) {
        val email = normalizeEmail(request.email)
        advisoryLockService.lockEmail(email)

        val existing = accounts.findById(request.userId).orElse(null)
        if (existing == null) {
            val conflicting = accounts.findByEmail(email)
            if (conflicting != null && conflicting.id != request.userId) {
                throw CredentialProvisionConflictException()
            }

            val now = Instant.now()
            val internalPassword = opaqueTokenService.issue().raw
            val passwordHash = requireNotNull(passwordEncoder.encode(internalPassword)) {
                "Password encoder returned null"
            }

            accounts.save(
                UserAccount(
                    id = request.userId,
                    email = email,
                    passwordHash = passwordHash,
                    emailVerified = false,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            return
        }

        if (!existing.email.equals(email, ignoreCase = true)) {
            throw CredentialProvisionConflictException()
        }

        if (!existing.identityProvisioned) {
            existing.identityProvisioned = true
            existing.updatedAt = Instant.now()
            accounts.save(existing)
        }
    }

    private fun normalizeEmail(email: String): String =
        email.trim().lowercase()
}
