package com.pavex.auth.bootstrap

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "pavex.bootstrap-admin",
    name = ["enabled"],
    havingValue = "true",
)
class BootstrapAdmin(
    private val accounts: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${pavex.bootstrap-admin.id}")
    private val configuredAdminId: String,
    @Value("\${pavex.bootstrap-admin.email}")
    private val configuredEmail: String,
    @Value("\${pavex.bootstrap-admin.password}")
    private val configuredPassword: String,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val adminId = UUID.fromString(configuredAdminId.trim())
        val email = configuredEmail.trim().lowercase()
        val password = configuredPassword

        require(email.isNotBlank()) {
            "pavex.bootstrap-admin.email must not be blank"
        }
        require(password.length in 8..128) {
            "pavex.bootstrap-admin.password must be between 8 and 128 characters"
        }

        val byId = accounts.findById(adminId).orElse(null)
        val byEmail = accounts.findByEmail(email)

        require(byId == null || byId.email.equals(email, ignoreCase = true)) {
            "Bootstrap admin id $adminId already belongs to another email"
        }
        require(byEmail == null || byEmail.id == adminId) {
            "Bootstrap admin email $email already belongs to another user id"
        }

        val now = Instant.now()
        val passwordHash = requireNotNull(passwordEncoder.encode(password)) {
            "Password encoder returned null"
        }

        val account = byId ?: UserAccount(
            id = adminId,
            email = email,
            passwordHash = passwordHash,
            emailVerified = true,
            identityProvisioned = true,
            createdAt = now,
            updatedAt = now,
        )

        account.email = email
        account.passwordHash = passwordHash
        account.emailVerified = true
        account.identityProvisioned = true
        account.failedLoginAttempts = 0
        account.lockoutEndAt = null
        account.updatedAt = now

        accounts.save(account)

        log.info("Bootstrap admin credential is ready: {} ({})", email, adminId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(BootstrapAdmin::class.java)
    }
}
