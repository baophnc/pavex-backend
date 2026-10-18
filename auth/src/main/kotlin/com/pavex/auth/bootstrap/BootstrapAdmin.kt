package com.pavex.auth.bootstrap

import com.pavex.auth.entity.UserAccount
import com.pavex.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
@Order(100)
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
    @Value("\${pavex.bootstrap-admin.password:}")
    private val configuredPassword: String,
    @Value("\${pavex.bootstrap-admin.reset-password:false}")
    private val resetPassword: Boolean,
) : ApplicationRunner {

    init {
        // Enabled without PAVEX_BOOTSTRAPADMIN_PASSWORD: stop startup instead of failing later or never.
        requireBootstrapPassword()
    }

    @Transactional
    override fun run(args: ApplicationArguments) {
        val adminId = UUID.fromString(configuredAdminId.trim())
        val email = configuredEmail.trim().lowercase()

        require(email.isNotBlank()) {
            "pavex.bootstrap-admin.email must not be blank"
        }

        val byId = accounts.findById(adminId).orElse(null)
        val byEmail = accounts.findByEmail(email)

        require(byId == null || byId.email.equals(email, ignoreCase = true)) {
            "Bootstrap admin id $adminId already belongs to another email"
        }
        require(byEmail == null || byEmail.id == adminId) {
            "Bootstrap admin email $email already belongs to another user id"
        }

        if (byId == null) {
            val password = requireBootstrapPassword()
            val now = Instant.now()
            accounts.save(
                UserAccount(
                    id = adminId,
                    email = email,
                    passwordHash = encodePassword(password),
                    emailVerified = true,
                    identityProvisioned = true,
                    failedLoginAttempts = 0,
                    lockoutEndAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            log.info("Created bootstrap admin credential: {} ({})", email, adminId)
            return
        }

        var changed = false
        val now = Instant.now()

        if (byId.email != email) {
            byId.email = email
            changed = true
        }
        if (!byId.emailVerified) {
            byId.emailVerified = true
            changed = true
        }
        if (!byId.identityProvisioned) {
            byId.identityProvisioned = true
            changed = true
        }
        if (byId.failedLoginAttempts != 0) {
            byId.failedLoginAttempts = 0
            changed = true
        }
        if (byId.lockoutEndAt != null) {
            byId.lockoutEndAt = null
            changed = true
        }

        if (resetPassword && !passwordEncoder.matches(requireBootstrapPassword(), byId.passwordHash)) {
            byId.passwordHash = encodePassword(requireBootstrapPassword())
            changed = true
            log.warn(
                "Reset bootstrap admin password because pavex.bootstrap-admin.reset-password=true: {} ({})",
                email,
                adminId,
            )
        }

        if (changed) {
            byId.updatedAt = now
            accounts.save(byId)
        }

        log.info(
            "Bootstrap admin credential is ready: {} ({}){}",
            email,
            adminId,
            if (resetPassword) " [configured password verified]" else "",
        )
    }

    private fun requireBootstrapPassword(): String {
        val password = configuredPassword
        require(password.length in MIN_BOOTSTRAP_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) {
            "pavex.bootstrap-admin.password (PAVEX_BOOTSTRAPADMIN_PASSWORD) must be between $MIN_BOOTSTRAP_PASSWORD_LENGTH and $MAX_PASSWORD_LENGTH characters when the bootstrap admin is enabled"
        }
        return password
    }

    private fun encodePassword(password: String): String =
        requireNotNull(passwordEncoder.encode(password)) {
            "Password encoder returned null"
        }

    companion object {
        private const val MIN_BOOTSTRAP_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 128
        private val log = LoggerFactory.getLogger(BootstrapAdmin::class.java)
    }
}
