package com.pavex.auth.service

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.exception.CredentialProvisionConflictException
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import com.pavex.contracts.identity.CredentialInvitationRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class InternalCredentialService(
    private val accounts: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val opaqueTokenService: OpaqueTokenService,
    private val invitationService: InvitationService,
    private val sessionService: SessionService,
    private val advisoryLockService: AdvisoryLockService,
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
            return
        }

        val byEmail = accounts.findByEmail(email)
        if (byEmail != null && byEmail.id != request.userId) {
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
    }

    @Transactional
    fun sendInvitation(request: CredentialInvitationRequest) {
        ensureCredential(request)
        invitationService.sendInvite(request.userId)
    }

    @Transactional
    fun revokeSessions(userId: UUID) {
        sessionService.logoutAll(userId)
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
    }

    private fun normalizeEmail(email: String): String =
        email.trim().lowercase()
}
