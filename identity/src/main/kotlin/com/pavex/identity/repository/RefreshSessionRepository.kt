package com.pavex.identity.repository

import com.pavex.identity.entity.RefreshSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RefreshSessionRepository :
    JpaRepository<RefreshSession, UUID> {

    fun findByTokenHash(
        tokenHash: String,
    ): RefreshSession?
}