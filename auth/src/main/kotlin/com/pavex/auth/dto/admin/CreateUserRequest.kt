package com.pavex.auth.dto.admin

import com.pavex.auth.entity.Gender
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateUserRequest(

    @field:NotBlank(
        message = "Email is required",
    )
    @field:Email(
        message = "Email is invalid",
    )
    @field:Size(
        max = 255,
        message = "Email must not exceed 255 characters",
    )
    val email: String,

    @field:NotBlank(
        message = "Role code is required",
    )
    @field:Size(
        max = 64,
        message = "Role code must not exceed 64 characters",
    )
    val roleCode: String,

    @field:Size(
        max = 100,
        message = "First name must not exceed 100 characters",
    )
    val firstName: String? = null,

    @field:Size(
        max = 100,
        message = "Last name must not exceed 100 characters",
    )
    val lastName: String? = null,

    @field:Size(
        max = 200,
        message = "Display name must not exceed 200 characters",
    )
    val displayName: String? = null,

    val gender: Gender =
        Gender.UNSPECIFIED,

    @field:PastOrPresent(
        message = "Date of birth cannot be in the future",
    )
    val dateOfBirth: LocalDate? = null,

    @field:Size(
        max = 2048,
        message = "Avatar URL must not exceed 2048 characters",
    )
    val avatarUrl: String? = null,
)
