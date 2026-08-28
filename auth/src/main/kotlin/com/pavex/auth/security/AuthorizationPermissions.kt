package com.pavex.auth.security

object AuthorizationPermissions {

    object Auth {

        const val READ_USERS =
            "auth.users.read"

        const val MANAGE_USER_PROFILE =
            "auth.users.manage-profile"

        const val READ_ROLES =
            "auth.roles.read"

        const val CREATE_USERS =
            "auth.users.create"

        const val INVITE_USERS =
            "auth.users.invite"

        const val ASSIGN_ROLE =
            "auth.roles.assign"

        const val MANAGE_USER_STATUS =
            "auth.users.manage-status"
    }
}
