package com.pavex.auth.repository

import com.pavex.auth.entity.AuthAuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AuthAuditLogRepository :
    JpaRepository<AuthAuditLog, UUID> {

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true,
    )
    @Query(
        """
        DELETE FROM AuthAuditLog audit
        WHERE audit.createdAt < :cutoff
        """
    )
    fun deleteBefore(
        @Param("cutoff")
        cutoff: Instant,
    ): Int
}
