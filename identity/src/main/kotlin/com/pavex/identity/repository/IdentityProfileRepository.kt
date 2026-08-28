package com.pavex.identity.repository

import com.pavex.identity.entity.IdentityProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IdentityProfileRepository : JpaRepository<IdentityProfile, UUID> {
    fun existsByEmailIgnoreCase(email: String): Boolean
}
