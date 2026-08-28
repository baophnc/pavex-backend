package com.pavex.auth.dto.admin

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UserDetailResponse(

    val userId: UUID,

    val email: String,

    val emailVerified: Boolean,

    val status: String,

    val profileStatus: String,

    val firstName: String?,

    val lastName: String?,

    val displayName: String?,

    val gender: String?,

    val dateOfBirth: LocalDate?,

    val avatarUrl: String?,

    val roles: List<String>,

    val permissions: List<String>,

    val lastLoginAt: Instant?,

    val failedLoginAttempts: Int,

    val lockoutEndAt: Instant?,

    val createdAt: Instant,

    val updatedAt: Instant,

    val version: Long,

    val profileCreatedAt: Instant?,

    val profileUpdatedAt: Instant?,
)
