package com.pavex.auth.dto.auth

/**
 * The one password rule every portal and the app check too: 8 to 128 characters with at least one
 * letter (any alphabet, so Vietnamese letters count) and one digit.
 */
object PasswordRules {
    const val PATTERN = "^(?=.*\\p{L})(?=.*\\d).{8,128}$"
    const val MESSAGE = "Mật khẩu cần 8–128 ký tự, có cả chữ và số."
}
