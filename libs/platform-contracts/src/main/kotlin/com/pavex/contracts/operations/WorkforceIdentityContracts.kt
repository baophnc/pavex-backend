package com.pavex.contracts.operations

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WorkforceIdentitySnapshotRequest(
    val userIds: List<UUID>,
    val search: String? = null,
)

data class WorkforceIdentitySnapshot(
    val userId: UUID,
    val email: String,
    val emailVerified: Boolean,
    val accountStatus: String,
    val profileStatus: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val gender: String? = null,
    val dateOfBirth: LocalDate? = null,
    val avatarUrl: String? = null,
    val roles: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)
