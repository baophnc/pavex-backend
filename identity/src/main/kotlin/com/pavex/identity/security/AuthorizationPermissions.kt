package com.pavex.identity.security

object AuthorizationPermissions {

    object Identity {
        const val READ_USERS = "identity.users.read"
        const val MANAGE_USER_PROFILE = "identity.users.manage-profile"
        const val READ_ROLES = "identity.roles.read"
        const val CREATE_USERS = "identity.users.create"
        const val INVITE_USERS = "identity.users.invite"
        const val ASSIGN_ROLE = "identity.roles.assign"
        const val MANAGE_USER_STATUS = "identity.users.manage-status"
    }
}
