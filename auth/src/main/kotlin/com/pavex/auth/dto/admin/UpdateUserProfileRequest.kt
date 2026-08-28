package com.pavex.auth.dto.admin

import com.pavex.auth.entity.Gender
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpdateUserProfileRequest(

    @field:Size(
        max = 100,
        message =
            "First name must not exceed 100 characters",
    )
    val firstName: String?,

    @field:Size(
        max = 100,
        message =
            "Last name must not exceed 100 characters",
    )
    val lastName: String?,

    @field:Size(
        max = 200,
        message =
            "Display name must not exceed 200 characters",
    )
    val displayName: String?,

    val gender: Gender,

    @field:PastOrPresent(
        message =
            "Date of birth cannot be in the future",
    )
    val dateOfBirth: LocalDate?,

    @field:Size(
        max = 2048,
        message =
            "Avatar URL must not exceed 2048 characters",
    )
    val avatarUrl: String?,
)
