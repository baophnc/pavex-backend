package com.pavex.auth.bootstrap

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.lang.reflect.Proxy
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BootstrapAdminTest {

    @Test
    fun `existing bootstrap admin keeps password hash unless reset is explicit`() {
        val adminId = UUID.randomUUID()
        val account = UserAccount(
            id = adminId,
            email = "admin@pavex.com",
            passwordHash = "existing-hash",
            emailVerified = true,
            identityProvisioned = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        var encodeCalls = 0

        val bootstrap = BootstrapAdmin(
            accounts = repository(account),
            passwordEncoder = encoder { value ->
                encodeCalls += 1
                "encoded:$value"
            },
            configuredAdminId = adminId.toString(),
            configuredEmail = account.email,
            configuredPassword = "1205@Phuc",
            resetPassword = false,
        )

        bootstrap.run(emptyArguments())

        assertEquals(0, encodeCalls)
        assertEquals("existing-hash", account.passwordHash)
    }

    @Test
    fun `explicit bootstrap reset rotates existing password hash`() {
        val adminId = UUID.randomUUID()
        val account = UserAccount(
            id = adminId,
            email = "admin@pavex.com",
            passwordHash = "existing-hash",
            emailVerified = true,
            identityProvisioned = true,
        )

        val bootstrap = BootstrapAdmin(
            accounts = repository(account),
            passwordEncoder = encoder { value -> "encoded:$value" },
            configuredAdminId = adminId.toString(),
            configuredEmail = account.email,
            configuredPassword = "1205@Phuc",
            resetPassword = true,
        )

        bootstrap.run(emptyArguments())

        assertNotEquals("existing-hash", account.passwordHash)
        assertEquals("encoded:1205@Phuc", account.passwordHash)
    }

    @Test
    fun `local reset repairs password verification and lockout and is idempotent`() {
        val encoder = BCryptPasswordEncoder()
        val account = UserAccount(
            id = UUID.randomUUID(),
            email = "admin@pavex.com",
            passwordHash = requireNotNull(encoder.encode("old-password")),
            emailVerified = false,
            identityProvisioned = false,
            failedLoginAttempts = 5,
            lockoutEndAt = Instant.now().plusSeconds(900),
        )
        val bootstrap = BootstrapAdmin(repository(account), encoder, account.id.toString(), account.email, "1205@Phuc", true)
        bootstrap.run(emptyArguments())
        assertTrue(encoder.matches("1205@Phuc", account.passwordHash))
        assertTrue(account.emailVerified)
        assertTrue(account.identityProvisioned)
        assertEquals(0, account.failedLoginAttempts)
        assertEquals(null, account.lockoutEndAt)
        val hash = account.passwordHash
        bootstrap.run(emptyArguments())
        assertEquals(hash, account.passwordHash)
    }

    @Test
    fun `bootstrap creates a credential whose configured password actually matches`() {
        val encoder = BCryptPasswordEncoder()
        val id = UUID.randomUUID()
        var saved: UserAccount? = null
        val accounts = proxy<UserAccountRepository> { method, args ->
            when (method) {
                "findById" -> Optional.empty<UserAccount>()
                "findByEmail" -> null
                "save" -> (args?.first() as UserAccount).also { saved = it }
                else -> defaultValue(method)
            }
        }
        BootstrapAdmin(accounts, encoder, id.toString(), " ADMIN@PAVEX.COM ", "1205@Phuc", true)
            .run(emptyArguments())
        val account = requireNotNull(saved)
        assertEquals(id, account.id)
        assertEquals("admin@pavex.com", account.email)
        assertTrue(encoder.matches("1205@Phuc", account.passwordHash))
        assertTrue(account.emailVerified && account.identityProvisioned)
    }

    @Test
    fun `bootstrap refuses to take over an email with a different id`() {
        val account = UserAccount(email = "admin@pavex.com", passwordHash = "old-hash")
        assertThrows(IllegalArgumentException::class.java) {
            BootstrapAdmin(repository(account), BCryptPasswordEncoder(), UUID.randomUUID().toString(), account.email, "1205@Phuc", true)
                .run(emptyArguments())
        }
        assertEquals("old-hash", account.passwordHash)
    }

    private fun repository(account: UserAccount): UserAccountRepository =
        proxy { method, args ->
            when (method) {
                "findById" -> Optional.ofNullable(account.takeIf { it.id == args?.firstOrNull() })
                "findByEmail" -> account.takeIf {
                    it.email.equals(args?.firstOrNull() as? String, ignoreCase = true)
                }
                "save" -> args?.first()
                else -> defaultValue(method)
            }
        }

    private fun encoder(encode: (CharSequence) -> String): PasswordEncoder =
        object : PasswordEncoder {
            override fun encode(rawPassword: CharSequence?): String = encode(requireNotNull(rawPassword))
            override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean = false
        }

    private fun emptyArguments(): ApplicationArguments =
        object : ApplicationArguments {
            override fun getSourceArgs(): Array<String> = emptyArray()
            override fun getOptionNames(): Set<String> = emptySet()
            override fun containsOption(name: String): Boolean = false
            override fun getOptionValues(name: String): List<String>? = null
            override fun getNonOptionArgs(): List<String> = emptyList()
        }

    private inline fun <reified T> proxy(
        crossinline handler: (String, Array<out Any?>?) -> Any?,
    ): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { instance, method, args ->
        when (method.name) {
            "toString" -> "${T::class.simpleName}TestProxy"
            "hashCode" -> System.identityHashCode(instance)
            "equals" -> instance === args?.firstOrNull()
            else -> handler(method.name, args)
        }
    } as T

    private fun defaultValue(method: String): Any? = when {
        method.startsWith("exists") -> false
        method.startsWith("count") -> 0L
        method.startsWith("findAll") -> emptyList<Any>()
        method.startsWith("deleteAll") -> 0
        else -> null
    }
}
