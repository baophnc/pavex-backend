package com.pavex.identity.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "role",
    schema = "identity",
)
class Role(

    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(
        nullable = false,
        unique = true,
        length = 64,
    )
    var code: String = "",

    @Column(
        nullable = false,
        length = 128,
    )
    var name: String = "",

    @Column(length = 512)
    var description: String? = null,

    @Column(
        name = "is_system",
        nullable = false,
    )
    var isSystem: Boolean = true,
)