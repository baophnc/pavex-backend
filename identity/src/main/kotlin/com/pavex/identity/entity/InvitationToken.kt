package com.pavex.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "invitation_token",
    schema = "identity",
)
class InvitationToken(

    @Id
    var id: UUID =
        UUID.randomUUID(),

    @Column(
        name = "user_id",
        nullable = false,
    )
    var userId: UUID,

    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        length = 128,
    )
    var tokenHash: String,

    @Column(
        name = "expires_at",
        nullable = false,
    )
    var expiresAt: Instant,

    @Column(
        name = "used_at",
    )
    var usedAt: Instant? = null,

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant,
)
