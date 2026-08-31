package com.pavex.operations.exception

import org.springframework.http.HttpStatus

open class OperationsApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class OperationsNotFoundException(code: String, message: String) :
    OperationsApiException(HttpStatus.NOT_FOUND, code, message)

class OperationsConflictException(code: String, message: String) :
    OperationsApiException(HttpStatus.CONFLICT, code, message)

class InvalidOperationsRequestException(code: String, message: String) :
    OperationsApiException(HttpStatus.BAD_REQUEST, code, message)

class OperationsUnavailableException(code: String, message: String) :
    OperationsApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message)
