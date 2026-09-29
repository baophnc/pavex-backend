package com.pavex.common.security

import java.util.UUID

data class PavexActor(
    val userId: UUID,
    val authorities: Set<String>,
) {
    fun hasAuthority(authority: String): Boolean = authority in authorities

    fun hasAnyAuthority(vararg required: String): Boolean = required.any(authorities::contains)

    fun hasRole(roleCode: String): Boolean = "ROLE_${roleCode.uppercase()}" in authorities

    fun hasAnyRole(vararg roleCodes: String): Boolean = roleCodes.any(::hasRole)

    /** Head-office users see every hub; everyone else only the hubs they are assigned to. */
    val seesAllHubs: Boolean get() = hasAuthority(ALL_HUBS_SCOPE)

    companion object {
        const val ALL_HUBS_SCOPE = "scope.all-hubs"

        fun parse(subject: String?, authorities: Iterable<String>): PavexActor? {
            val userId = subject?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
            return PavexActor(userId, authorities.toCollection(linkedSetOf()))
        }
    }
}
