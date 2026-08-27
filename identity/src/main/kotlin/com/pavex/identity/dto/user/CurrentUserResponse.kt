package com.pavex.identity.dto.user

import java.time.LocalDate
import java.util.UUID

data class CurrentUserResponse(

    val userId: UUID,

    val email: String,

    val status: String,

    val profileStatus: String,

    val emailVerified: Boolean,

    val firstName: String?,

    val lastName: String?,

    val displayName: String?,

    val gender: String,

    val dateOfBirth: LocalDate?,

    val avatarUrl: String?,

    val roles: Set<String>,

    val permissions: Set<String>,
)