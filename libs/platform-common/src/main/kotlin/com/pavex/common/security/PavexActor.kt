package com.pavex.common.security

import java.util.UUID

data class PavexActor(
    val userId: UUID,
    val authorities: Set<String>,
) {
    fun hasAuthority(authority: String): Boolean = authority in authorities

    fun hasAnyAuthority(vararg required: String): Boolean = required.any(authorities::contains)

    fun hasRole(roleCode: String): Boolean = "ROLE_${roleCode.uppercase()}" in authorities

    companion object {
        fun parse(subject: String?, authorities: Iterable<String>): PavexActor? {
            val userId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
            return PavexActor(userId, authorities.toCollection(linkedSetOf()))
        }
    }
}
