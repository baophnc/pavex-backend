package com.pavex.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "external_identity",
    schema = "auth",
    uniqueConstraints = [
        UniqueConstraint(
            name = "ux_external_identity_provider_user",
            columnNames = [
                "provider",
                "provider_user_id",
            ],
        ),
        UniqueConstraint(
            name = "ux_external_identity_user_provider",
            columnNames = [
                "user_id",
                "provider",
            ],
        ),
    ],
)
class ExternalIdentity(

    @Id
    var id: UUID =
        UUID.randomUUID(),

    @Column(
        name = "user_id",
        nullable = false,
    )
    var userId: UUID,

    @Column(
        nullable = false,
        length = 50,
    )
    var provider: String,

    @Column(
        name = "provider_user_id",
        nullable = false,
        length = 255,
    )
    var providerUserId: String,

    @Column(
        length = 320,
    )
    var email: String? = null,

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant,
)
