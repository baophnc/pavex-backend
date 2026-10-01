package com.pavex.contracts.identity

/**
 * Identity tells Auth that an account's sign-in email changed: to a new address an administrator
 * typed, or to an anonymous placeholder when the account was deleted.
 */
data class CredentialEmailRequest(
    val email: String,
)
