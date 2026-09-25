package com.pavex.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "refresh_session",
    schema = "auth",
)
class RefreshSession(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        name = "user_id",
        nullable = false,
    )
    var userId: UUID = UUID.randomUUID(),

    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        length = 128,
    )
    var tokenHash: String = "",

    @Column(
        name = "expires_at",
        nullable = false,
    )
    var expiresAt: Instant = Instant.now(),

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant = Instant.now(),

    /**
     * Login time of the whole rotation chain. Copied to every replacement so the chain
     * has an absolute lifetime no matter how often it is refreshed.
     */
    @Column(
        name = "session_started_at",
        nullable = false,
    )
    var sessionStartedAt: Instant = createdAt,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "replaced_by_session_id")
    var replacedBySessionId: UUID? = null,

    @Column(
        name = "ip_address",
        length = 64,
    )
    var ipAddress: String? = null,

    @Column(
        name = "user_agent",
        length = 512,
    )
    var userAgent: String? = null,
    @Version
    @Column(nullable = false)
    var version: Long = 0,
)