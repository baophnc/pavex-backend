package com.pavex.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "email_verification_token",
    schema = "auth",
)
class EmailVerificationToken(

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

    @Column(name = "used_at")
    var usedAt: Instant? = null,
)
