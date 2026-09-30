package com.pavex.auth.service

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.repository.UserAccountRepository
import com.pavex.auth.security.OpaqueTokenService
import com.pavex.contracts.identity.CredentialInvitationRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID

class StaffTemporaryPasswordTest {
    private val encoder = BCryptPasswordEncoder()
    private var saved: UserAccount? = null
    private val accounts = mock(UserAccountRepository::class.java) { call ->
        when (call.method.name) {
            "findById" -> Optional.empty<UserAccount>()
            "findByEmail" -> null
            "save" -> (call.arguments[0] as UserAccount).also { saved = it }
            else -> RETURNS_DEFAULTS.answer(call)
        }
    }
    private val service = InternalCredentialService(
        accounts,
        encoder,
        OpaqueTokenService(),
        mock(InvitationService::class.java),
        mock(SessionService::class.java),
        mock(AdvisoryLockService::class.java),
        "123456",
        Duration.ofHours(72),
    )

    @Test
    fun `an account an administrator creates signs in with the temporary password and must change it`() {
        val before = Instant.now()
        service.provisionInvitation(CredentialInvitationRequest(UUID.randomUUID(), "Hung.PhanCong.TBC@pavex.com"))

        val account = saved!!
        assertEquals("hung.phancong.tbc@pavex.com", account.email)
        assertTrue(encoder.matches("123456", account.passwordHash))
        assertTrue(account.emailVerified)
        assertTrue(account.mustChangePassword)
        val expires = account.temporaryPasswordExpiresAt!!
        assertFalse(expires.isBefore(before.plus(Duration.ofHours(72))))
        assertTrue(expires.isBefore(Instant.now().plus(Duration.ofHours(73))))
    }
}
