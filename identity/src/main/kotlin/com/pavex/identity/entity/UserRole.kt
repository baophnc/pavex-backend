package com.pavex.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class UserRoleId(
    var userId: UUID? = null,
    var roleId: UUID? = null,
) : Serializable


@Entity
@IdClass(UserRoleId::class)
@Table(
    name = "user_role",
    schema = "identity",
)
class UserRole(

    @Id
    @Column(name = "user_id")
    var userId: UUID = UUID.randomUUID(),

    @Id
    @Column(name = "role_id")
    var roleId: UUID = UUID.randomUUID(),

    @Column(
        name = "assigned_at",
        nullable = false,
    )
    var assignedAt: Instant = Instant.now(),
)
