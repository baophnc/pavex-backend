package com.pavex.auth.dto.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PasswordRulesTest {
    private val rule = Regex(PasswordRules.PATTERN)

    @Test
    fun `8 to 128 characters with a letter and a digit`() {
        assertTrue(rule.matches("matkhau1"))
        assertTrue(rule.matches("Pavex2026!"))
        assertTrue(rule.matches("đườngdây9"))
        assertTrue(rule.matches("a1".repeat(64)))
    }

    @Test
    fun `too short, too long, or missing a letter or a digit is refused`() {
        assertFalse(rule.matches("abc123"))
        assertFalse(rule.matches("a1".repeat(64) + "x"))
        assertFalse(rule.matches("12345678"))
        assertFalse(rule.matches("abcdefgh"))
    }
}
