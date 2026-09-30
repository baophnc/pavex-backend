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
    name = "user_account",
    schema = "auth",
)
class UserAccount(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        nullable = false,
        unique = true,
        length = 255,
    )
    var email: String = "",

    @Column(
        name = "password_hash",
        nullable = false,
        length = 512,
    )
    var passwordHash: String = "",

    @Column(
        name = "email_verified",
        nullable = false,
    )
    var emailVerified: Boolean = false,

    @Column(
        name = "identity_provisioned",
        nullable = false,
    )
    var identityProvisioned: Boolean = false,

    @Column(
        name = "failed_login_attempts",
        nullable = false,
    )

    var failedLoginAttempts: Int = 0,

    @Column(name = "lockout_end_at")
    var lockoutEndAt: Instant? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    /** Set for accounts created by an administrator with the temporary password. */
    @Column(
        name = "must_change_password",
        nullable = false,
    )
    var mustChangePassword: Boolean = false,

    @Column(name = "temporary_password_expires_at")
    var temporaryPasswordExpiresAt: Instant? = null,

    @Column(
        name = "created_at",
        nullable = false,
    )
    var createdAt: Instant = Instant.now(),

    @Column(
        name = "updated_at",
        nullable = false,
    )
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
