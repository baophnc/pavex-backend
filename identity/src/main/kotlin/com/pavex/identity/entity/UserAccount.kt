package com.pavex.identity.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_account",
    schema = "identity",
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

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 32,
    )
    var status: AccountStatus = AccountStatus.ACTIVE,

    @Column(
        name = "email_verified",
        nullable = false,
    )
    var emailVerified: Boolean = false,

    @Column(
        name = "failed_login_attempts",
        nullable = false,
    )
    var failedLoginAttempts: Int = 0,

    @Column(name = "lockout_end_at")
    var lockoutEndAt: Instant? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

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

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
}