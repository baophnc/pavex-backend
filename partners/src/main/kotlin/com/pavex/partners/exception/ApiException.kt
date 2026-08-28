package com.pavex.partners.exception

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

data class ApiErrorResponse(
    val code: String,
    val message: String,
)
