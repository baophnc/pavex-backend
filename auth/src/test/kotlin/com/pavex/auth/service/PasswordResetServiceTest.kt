package com.pavex.auth.service

import com.pavex.auth.entity.PasswordResetToken
import com.pavex.auth.entity.UserAccount
import com.pavex.auth.event.PasswordResetPortal
import com.pavex.auth.event.PasswordResetRequestedEvent
import com.pavex.auth.exception.InvalidPasswordResetTokenException
import com.pavex.auth.repository.InvitationTokenRepository
import com.pavex.auth.repository.PasswordResetTokenRepository
import com.pavex.auth.repository.RefreshSessionRepository
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Duration
import java.time.Instant
import java.util.Optional

class PasswordResetServiceTest {
    private val opaque = OpaqueTokenService()
    private val encoder = BCryptPasswordEncoder()
    private val account = UserAccount(email = "nguyen.van.hung@demo.pavex.local", passwordHash = "old-hash", emailVerified = false, identityProvisioned = true, failedLoginAttempts = 4, lockoutEndAt = Instant.now().plusSeconds(600))
    private val token = PasswordResetToken(userId = account.id, tokenHash = opaque.hash("valid-token"), expiresAt = Instant.now().plusSeconds(600))
    private var tokenAvailable = true
    private var accountExists = true
    private val accounts = mock(UserAccountRepository::class.java) { call -> when (call.method.name) {
        "findByEmail" -> if (accountExists) account else null
        "findById" -> Optional.of(account)
        "save" -> call.arguments[0]
        else -> RETURNS_DEFAULTS.answer(call)
    } }
    private val tokens = mock(PasswordResetTokenRepository::class.java) { call -> when (call.method.name) {
        "findByTokenHash" -> if (call.arguments[0] == token.tokenHash) token else null
        "consumeIfValid" -> if (tokenAvailable && token.expiresAt.isAfter(Instant.now())) { tokenAvailable = false; 1 } else 0
        else -> RETURNS_DEFAULTS.answer(call)
    } }
    private val sessions = mock(RefreshSessionRepository::class.java)
    private val invitations = mock(InvitationTokenRepository::class.java)
    private val outbox = mock(OutboxService::class.java)
    private val locks = mock(AdvisoryLockService::class.java)
    private val sync = mock(IdentityStateSyncService::class.java)
    private val service = PasswordResetService(tokens, accounts, sessions, opaque, encoder, outbox, locks, invitations, sync, Duration.ofHours(1))

    @Test
    fun `admin-created user can set first password and prove email ownership`() {
        service.resetPassword("valid-token", "MatKhauMoi@123")
        assertTrue(encoder.matches("MatKhauMoi@123", account.passwordHash))
        assertTrue(account.emailVerified)
        assertTrue(account.identityProvisioned)
        assertEquals(0, account.failedLoginAttempts)
        assertNull(account.lockoutEndAt)
        verify(sync).emailVerifiedAfterCommit(account.id)
        assertTrue(mockingDetails(sessions).invocations.any { it.method.name == "revokeAllActiveByUserId" })
        assertTrue(mockingDetails(invitations).invocations.any { it.method.name == "invalidateUnusedByUserId" })
        assertEquals(listOf("lockPasswordReset", "lockInvitation", "lockUserSessions"), mockingDetails(locks).invocations.map { it.method.name })
    }

    @Test
    fun `resetting ends the temporary password an administrator issued`() {
        account.mustChangePassword = true
        account.temporaryPasswordExpiresAt = Instant.now().plusSeconds(3600)
        service.resetPassword("valid-token", "MatKhauMoi@123")
        assertFalse(account.mustChangePassword)
        assertNull(account.temporaryPasswordExpiresAt)
    }

    @Test
    fun `token cannot be reused to change password again`() {
        service.resetPassword("valid-token", "MatKhauMoi@123")
        val hash = account.passwordHash
        assertThrows(InvalidPasswordResetTokenException::class.java) { service.resetPassword("valid-token", "Attacker@123") }
        assertEquals(hash, account.passwordHash)
    }

    @Test
    fun `expired token cannot verify email or change credentials`() {
        token.expiresAt = Instant.now().minusSeconds(1)
        assertThrows(InvalidPasswordResetTokenException::class.java) { service.resetPassword("valid-token", "MatKhauMoi@123") }
        assertEquals("old-hash", account.passwordHash)
        assertFalse(account.emailVerified)
        verifyNoInteractions(sync, invitations, sessions)
    }

    @Test
    fun `invalid token leaves account unchanged`() {
        assertThrows(InvalidPasswordResetTokenException::class.java) { service.resetPassword("unknown-token", "MatKhauMoi@123") }
        assertEquals("old-hash", account.passwordHash)
        verifyNoInteractions(sync, invitations, sessions)
    }

    @Test
    fun `unknown email has no observable token or email side effect`() {
        accountExists = false
        service.forgotPassword("unknown@demo.pavex.local", PasswordResetPortal.MANAGEMENT)
        verifyNoInteractions(tokens, outbox, locks)
    }

    @Test
    fun `management recovery keeps a whitelisted portal in the email event`() {
        service.forgotPassword(" NGUYEN.VAN.HUNG@DEMO.PAVEX.LOCAL ", PasswordResetPortal.MANAGEMENT)
        verify(accounts).findByEmail(account.email)
        val event = mockingDetails(outbox).invocations.single { it.method.name == "enqueue" }.arguments[1] as PasswordResetRequestedEvent
        assertEquals(PasswordResetPortal.MANAGEMENT, event.portal)
        assertEquals(account.email, event.email)
        assertTrue(event.rawToken.isNotBlank())
        assertEquals("old-hash", account.passwordHash)
        assertFalse(account.emailVerified)
    }
}
