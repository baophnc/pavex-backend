package com.pavex.identity.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class RolePermissionId(
    var roleId: UUID? = null,
    var permissionId: UUID? = null,
) : Serializable


@Entity
@IdClass(RolePermissionId::class)
@Table(
    name = "role_permission",
    schema = "identity",
)
class RolePermission(

    @Id
    @Column(name = "role_id")
    var roleId: UUID = UUID.randomUUID(),

    @Id
    @Column(name = "permission_id")
    var permissionId: UUID = UUID.randomUUID(),
)
