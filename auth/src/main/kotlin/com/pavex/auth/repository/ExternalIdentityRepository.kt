package com.pavex.auth.repository

import com.pavex.auth.entity.ExternalIdentity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalIdentityRepository :
    JpaRepository<ExternalIdentity, UUID> {

    fun findByProviderAndProviderUserId(
        provider: String,
        providerUserId: String,
    ): ExternalIdentity?

    fun findByUserIdAndProvider(
        userId: UUID,
        provider: String,
    ): ExternalIdentity?
}
