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

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 32,
    )
    var status: AccountStatus = AccountStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(
        name = "profile_status",
        nullable = false,
        length = 32,
    )
    var profileStatus: ProfileStatus =
        ProfileStatus.INCOMPLETE,
    @Column(
        name = "email_verified",
        nullable = false,
    )
    var emailVerified: Boolean = false,

    @Column(name = "authorization_version", nullable = false)
    var authorizationVersion: Long = 0,

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

    /** When and by whom the account was deleted; [deletedEmail] is the address it had then. */
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "deleted_by_user_id")
    var deletedByUserId: UUID? = null,

    @Column(name = "deleted_email", length = 255)
    var deletedEmail: String? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)

enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
    /** Deleted softly: kept for history, cannot sign in, hidden from the default lists. */
    DELETED,
}
enum class ProfileStatus {
    INCOMPLETE,
    COMPLETED,
    PENDING_VERIFICATION,
    REJECTED,
}
