package com.pavex.operations.service

import com.pavex.operations.entity.WorkforceMemberKind

/**
 * Each workforce kind is backed by exactly one Identity role of the same name,
 * so a member's kind and role can never drift apart.
 */
object WorkforceRolePolicy {
    fun onboardingRole(kind: WorkforceMemberKind): String = kind.name

    fun isCompatible(kind: WorkforceMemberKind, roles: Collection<String>): Boolean = kind.name in roles

    fun acceptedRoles(kind: WorkforceMemberKind): Set<String> = setOf(kind.name)
}
