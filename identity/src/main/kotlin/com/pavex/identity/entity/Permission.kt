package com.pavex.identity.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "permission",
    schema = "identity",
)
class Permission(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        nullable = false,
        unique = true,
        length = 128,
    )
    var code: String = "",

    @Column(
        nullable = false,
        length = 128,
    )
    var name: String = "",

    @Column(length = 512)
    var description: String? = null,
)