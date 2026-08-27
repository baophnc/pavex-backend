package com.pavex.identity.security.google

data class GoogleUserInfo(

    val subject: String,

    val email: String,

    val emailVerified: Boolean,

    val displayName: String?,

    val avatarUrl: String?,

    val hostedDomain: String?,

    val emailAuthoritative: Boolean,
)
