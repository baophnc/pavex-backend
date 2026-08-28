package com.pavex.identity.dto.user

import com.pavex.identity.entity.Gender
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateProfileRequest(

    @field:Size(
        max = 100,
        message = "First name must not exceed 100 characters",
    )
    val firstName: String?,

    @field:Size(
        max = 100,
        message = "Last name must not exceed 100 characters",
    )
    val lastName: String?,

    @field:Size(
        max = 200,
        message = "Display name must not exceed 200 characters",
    )
    val displayName: String?,

    val gender: Gender,

    val dateOfBirth: LocalDate?,

    @field:Size(
        max = 2048,
        message = "Avatar URL must not exceed 2048 characters",
    )
    val avatarUrl: String?,
)
