package com.pavex.identity.service

import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.Role
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.exception.AccountDeletedException
import com.pavex.identity.exception.ApiException
import com.pavex.identity.exception.LastActiveAdminException
import com.pavex.identity.repository.RoleRepository
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.repository.UserRoleRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mock
import java.util.Optional
import java.util.UUID

/** Deleting an account softly and changing its sign-in email, as an administrator. */
class AdminUserLifecycleTest {
    private val admin = UUID.randomUUID()
    private val accounts = mutableMapOf<UUID, UserAccount>()
    private val adminRole = Role(code = "ADMIN", name = "Administrator", isSystem = true)
    private val admins = mutableSetOf<UUID>()
    private val authCalls = mutableListOf<String>()

    private val service = AdminUserService(
        mock(UserAccountRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "findById" -> Optional.ofNullable(accounts[invocation.getArgument(0)])
                "save" -> invocation.getArgument<UserAccount>(0).also { accounts[it.id] = it }
                "existsByEmail" -> accounts.values.any { it.email == invocation.getArgument<String>(0) }
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        mock(UserProfileRepository::class.java),
        mock(UserRoleRepository::class.java) { invocation ->
            when (invocation.method.name) {
                "existsByUserIdAndRoleId" -> invocation.getArgument<UUID>(0) in admins
                "countActiveUsersWithRoleCode" -> admins.count { accounts[it]?.status == AccountStatus.ACTIVE }.toLong()
                else -> RETURNS_DEFAULTS.answer(invocation)
            }
        },
        mock(RoleRepository::class.java) { invocation ->
            if (invocation.method.name == "findByCodeForUpdate") adminRole else RETURNS_DEFAULTS.answer(invocation)
        },
        mock(RoleService::class.java),
        mock(AuthCredentialClient::class.java) { invocation ->
            authCalls += "${invocation.method.name}:${invocation.arguments.drop(1).joinToString()}"
            RETURNS_DEFAULTS.answer(invocation)
        },
    )

    private fun account(email: String, status: AccountStatus = AccountStatus.ACTIVE) =
        UserAccount(email = email, status = status, emailVerified = true).also { accounts[it.id] = it }

    @Test
    fun `a deleted account keeps its row, frees its email and closes its sign-in`() {
        val user = account("an@pavex.test")

        service.deleteUser(user.id, admin)

        val deleted = accounts.getValue(user.id)
        assertEquals(AccountStatus.DELETED, deleted.status)
        assertEquals("an@pavex.test", deleted.deletedEmail)
        assertEquals("deleted+${user.id}@pavex.invalid", deleted.email)
        assertEquals(admin, deleted.deletedByUserId)
        assertFalse(deleted.emailVerified)
        assertEquals(listOf("close:deleted+${user.id}@pavex.invalid"), authCalls)
    }

    @Test
    fun `nobody deletes themselves, and the last active admin stays`() {
        val self = account("admin@pavex.test").also { admins += it.id }

        val own = assertThrows(ApiException::class.java) { service.deleteUser(self.id, self.id) }
        assertEquals("cannot_delete_own_account", own.code)
        assertThrows(LastActiveAdminException::class.java) { service.deleteUser(self.id, admin) }
        assertEquals(AccountStatus.ACTIVE, accounts.getValue(self.id).status)
        assertEquals(emptyList<String>(), authCalls)
    }

    @Test
    fun `a deleted account cannot be reactivated or edited`() {
        val gone = account("deleted+x@pavex.invalid", AccountStatus.DELETED)

        assertThrows(AccountDeletedException::class.java) { service.changeStatus(gone.id, AccountStatus.ACTIVE) }
        assertThrows(AccountDeletedException::class.java) { service.changeEmail(gone.id, "moi@pavex.test") }
    }

    @Test
    fun `a new email must be free and is confirmed again`() {
        val user = account("cu@pavex.test")
        account("da-co@pavex.test")

        val taken = assertThrows(ApiException::class.java) { service.changeEmail(user.id, " Da-Co@pavex.test ") }
        assertEquals("email_already_exists", taken.code)

        service.changeEmail(user.id, " Moi@Pavex.test ")

        assertEquals("moi@pavex.test", accounts.getValue(user.id).email)
        assertFalse(accounts.getValue(user.id).emailVerified)
        assertEquals(listOf("changeEmail:moi@pavex.test"), authCalls)
    }
}
