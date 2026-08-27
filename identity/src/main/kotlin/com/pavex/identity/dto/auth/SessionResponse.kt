package com.pavex.identity.dto.auth

import java.time.Instant
import java.util.UUID

data class SessionResponse(
    val sessionId: UUID,
    val ipAddress: String?,
    val userAgent: String?,
    val expiresAt: Instant,
)
