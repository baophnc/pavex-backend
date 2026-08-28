package com.pavex.identity.service

import com.pavex.contracts.identity.IdentityAuthContext
import com.pavex.identity.dto.IdentityProfileResponse
import com.pavex.identity.dto.ProvisionIdentityRequest
import com.pavex.identity.dto.UpdateIdentityProfileRequest
import com.pavex.identity.entity.IdentityProfile
import com.pavex.identity.repository.IdentityProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class IdentityProfileService(
    private val repository: IdentityProfileRepository,
) {
    @Transactional
    fun provision(request: ProvisionIdentityRequest): IdentityProfileResponse {
        val normalizedEmail = request.email.trim().lowercase()
        val existing = repository.findById(request.userId).orElse(null)
        val profile = existing ?: IdentityProfile(userId = request.userId, createdAt = Instant.now())
        profile.email = normalizedEmail
        profile.status = request.status.trim().uppercase()
        profile.emailVerified = request.emailVerified
        profile.updatedAt = Instant.now()
        return repository.save(profile).toResponse()
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): IdentityProfileResponse = find(userId).toResponse()

    @Transactional
    fun update(userId: UUID, request: UpdateIdentityProfileRequest): IdentityProfileResponse {
        val profile = find(userId)
        profile.firstName = request.firstName?.trim()?.takeIf(String::isNotEmpty)
        profile.lastName = request.lastName?.trim()?.takeIf(String::isNotEmpty)
        profile.displayName = request.displayName?.trim()?.takeIf(String::isNotEmpty)
        profile.avatarUrl = request.avatarUrl?.trim()?.takeIf(String::isNotEmpty)
        profile.updatedAt = Instant.now()
        return repository.save(profile).toResponse()
    }

    @Transactional(readOnly = true)
    fun authContext(userId: UUID): IdentityAuthContext {
        val profile = find(userId)
        return IdentityAuthContext(
            userId = profile.userId,
            active = profile.status == "ACTIVE",
            emailVerified = profile.emailVerified,
            roles = emptySet(),
            permissions = emptySet(),
            authorizationVersion = profile.authorizationVersion,
        )
    }

    private fun find(userId: UUID): IdentityProfile =
        repository.findById(userId).orElseThrow { NoSuchElementException("Identity profile not found") }

    private fun IdentityProfile.toResponse() = IdentityProfileResponse(
        userId = userId,
        email = email,
        status = status,
        emailVerified = emailVerified,
        firstName = firstName,
        lastName = lastName,
        displayName = displayName,
        avatarUrl = avatarUrl,
        authorizationVersion = authorizationVersion,
    )
}
