package com.pavex.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_profile", schema = "identity")
class IdentityProfile(
    @Id
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 255)
    var email: String = "",

    @Column(nullable = false, length = 32)
    var status: String = "ACTIVE",

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    @Column(length = 100)
    var firstName: String? = null,

    @Column(length = 100)
    var lastName: String? = null,

    @Column(length = 200)
    var displayName: String? = null,

    @Column(length = 2048)
    var avatarUrl: String? = null,

    @Column(nullable = false)
    var authorizationVersion: Long = 0,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    var version: Long = 0,
)
