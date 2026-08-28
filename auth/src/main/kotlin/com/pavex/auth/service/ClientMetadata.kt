package com.pavex.auth.service

data class ClientMetadata(
    val ipAddress: String?,
    val userAgent: String?,
    val requestId: String?,
)
