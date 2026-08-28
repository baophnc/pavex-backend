package com.pavex.identity.service

import com.pavex.contracts.identity.IdentityAuthContext
import com.pavex.identity.dto.IdentityProfileResponse
import com.pavex.identity.dto.ProvisionIdentityRequest
import com.pavex.identity.dto.UpdateIdentityProfileRequest
import com.pavex.identity.entity.AccountStatus
import com.pavex.identity.entity.UserAccount
import com.pavex.identity.entity.UserProfile
import com.pavex.identity.exception.UserNotFoundException
import com.pavex.identity.repository.UserAccountRepository
import com.pavex.identity.repository.UserProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class IdentityProfileService(
    private val accounts: UserAccountRepository,
    private val profiles: UserProfileRepository,
    private val roleService: RoleService,
) {

    @Transactional
    fun provision(request: ProvisionIdentityRequest): IdentityProfileResponse {
        val normalizedEmail = request.email.trim().lowercase()
        val requestedStatus = AccountStatus.valueOf(request.status.trim().uppercase())
        val now = Instant.now()
        val existing = accounts.findById(request.userId).orElse(null)

        val account = if (existing == null) {
            accounts.save(
                UserAccount(
                    id = request.userId,
                    email = normalizedEmail,
                    status = requestedStatus,
                    emailVerified = request.emailVerified,
                    createdAt = now,
                    updatedAt = now,
                )
            ).also {
                roleService.assignDefaultCustomerRole(it.id, now)
            }
        } else {
            val authorizationChanged = existing.status != requestedStatus
            existing.email = normalizedEmail
            existing.status = requestedStatus
            existing.emailVerified = request.emailVerified
            if (authorizationChanged) {
                existing.authorizationVersion += 1
            }
            existing.updatedAt = now
            accounts.save(existing)
        }

        return toResponse(account, profiles.findById(account.id).orElse(null))
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): IdentityProfileResponse {
        val account = findAccount(userId)
        return toResponse(account, profiles.findById(userId).orElse(null))
    }

    @Transactional
    fun update(
        userId: UUID,
        request: UpdateIdentityProfileRequest,
    ): IdentityProfileResponse {
        val account = findAccount(userId)
        val now = Instant.now()
        val profile = profiles.findById(userId).orElseGet {
            UserProfile(
                userId = userId,
                createdAt = now,
                updatedAt = now,
            )
        }

        profile.firstName = normalize(request.firstName)
        profile.lastName = normalize(request.lastName)
        profile.displayName = normalize(request.displayName)
        profile.avatarUrl = normalize(request.avatarUrl)
        profile.updatedAt = now
        profiles.save(profile)

        return toResponse(account, profile)
    }

    @Transactional(readOnly = true)
    fun authContext(userId: UUID): IdentityAuthContext {
        val account = findAccount(userId)
        val authorization = roleService.getAuthorization(userId)

        return IdentityAuthContext(
            userId = account.id,
            active = account.status == AccountStatus.ACTIVE,
            emailVerified = account.emailVerified,
            roles = authorization.roles,
            permissions = authorization.permissions,
            authorizationVersion = account.authorizationVersion,
        )
    }

    private fun findAccount(userId: UUID): UserAccount =
        accounts.findById(userId).orElseThrow { UserNotFoundException() }

    private fun toResponse(
        account: UserAccount,
        profile: UserProfile?,
    ) = IdentityProfileResponse(
        userId = account.id,
        email = account.email,
        status = account.status.name,
        emailVerified = account.emailVerified,
        firstName = profile?.firstName,
        lastName = profile?.lastName,
        displayName = profile?.displayName,
        avatarUrl = profile?.avatarUrl,
        authorizationVersion = account.authorizationVersion,
    )

    private fun normalize(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }
}
