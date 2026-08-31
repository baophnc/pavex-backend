package com.pavex.common.api

/**
 * Stable lightweight error envelope shared by services that expose
 * `{ "code": "...", "message": "..." }` responses.
 *
 * Keep service-specific exception types inside their owning modules; only the
 * wire shape belongs in platform-common.
 */
data class ApiErrorResponse(
    val code: String,
    val message: String,
)
