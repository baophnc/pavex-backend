package com.pavex.auth.entity

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(
    name = "user_profile",
    schema = "auth",
)
class UserProfile(

    @Id
    @Column(name = "user_id")
    var userId: UUID = UUID.randomUUID(),

    @Column(
        name = "first_name",
        length = 100,
    )
    var firstName: String? = null,

    @Column(
        name = "last_name",
        length = 100,
    )
    var lastName: String? = null,

    @Column(
        name = "display_name",
        length = 200,
    )
    var displayName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 32,
    )
    var gender: Gender = Gender.UNSPECIFIED,

    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,

    @Column(
        name = "avatar_url",
        length = 2048,
    )
    var avatarUrl: String? = null,

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
)

enum class Gender {
    UNSPECIFIED,
    MALE,
    FEMALE,
    OTHER,
}