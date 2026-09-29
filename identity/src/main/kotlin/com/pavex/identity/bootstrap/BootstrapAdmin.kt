package com.pavex.identity.bootstrap

import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.Gender
import com.pavex.identity.entity.ProfileStatus
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import com.pavex.identity.service.RoleService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
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
    private val profiles: UserProfileRepository,
    private val roleService: RoleService,
    @Value("\${pavex.bootstrap-admin.id}")
    private val configuredAdminId: String,
    @Value("\${pavex.bootstrap-admin.email}")
    private val configuredEmail: String,
) : ApplicationRunner {

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

        val now = Instant.now()
        val account = byId ?: UserAccount(
            id = adminId,
            email = email,
            status = AccountStatus.ACTIVE,
            profileStatus = ProfileStatus.COMPLETED,
            emailVerified = true,
            createdAt = now,
            updatedAt = now,
        )

        val authorizationChanged =
            account.status != AccountStatus.ACTIVE ||
                !account.emailVerified ||
                !account.email.equals(email, ignoreCase = true)

        account.email = email
        account.status = AccountStatus.ACTIVE
        account.emailVerified = true
        account.profileStatus = ProfileStatus.COMPLETED
        account.updatedAt = now

        if (authorizationChanged) {
            account.authorizationVersion += 1
        }

        accounts.save(account)

        val profile = profiles.findById(adminId).orElseGet {
            UserProfile(
                userId = adminId,
                createdAt = now,
                updatedAt = now,
            )
        }

        if (profile.firstName.isNullOrBlank()) {
            profile.firstName = "Pavex"
        }
        if (profile.lastName.isNullOrBlank()) {
            profile.lastName = "Admin"
        }
        if (profile.displayName.isNullOrBlank()) {
            profile.displayName = "PAVEX Admin"
        }
        if (profile.gender == Gender.UNSPECIFIED) {
            profile.gender = Gender.OTHER
        }
        if (profile.dateOfBirth == null) {
            profile.dateOfBirth = DEFAULT_DATE_OF_BIRTH
        }
        profile.updatedAt = now

        profiles.save(profile)

        val addedAdminPermissions = roleService.synchronizeSystemAdminPermissions(now)
        roleService.assignBootstrapAdminRole(
            actorUserId = adminId,
            targetUserId = adminId,
        )

        log.info(
            "Bootstrap admin identity is ready: {} ({}) [admin permissions synchronized: +{}]",
            email,
            adminId,
            addedAdminPermissions,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(BootstrapAdmin::class.java)
        private val DEFAULT_DATE_OF_BIRTH = LocalDate.of(2000, 1, 1)
    }
}
