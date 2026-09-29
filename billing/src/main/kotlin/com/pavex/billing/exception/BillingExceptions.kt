package com.pavex.billing.exception

import org.springframework.http.HttpStatus

open class BillingApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class InvalidBillingRequestException(code: String, message: String) :
    BillingApiException(HttpStatus.BAD_REQUEST, code, message)

class BillingForbiddenException(code: String, message: String) :
    BillingApiException(HttpStatus.FORBIDDEN, code, message)

class BillingNotFoundException(code: String, message: String) :
    BillingApiException(HttpStatus.NOT_FOUND, code, message)

class BillingConflictException(code: String, message: String) :
    BillingApiException(HttpStatus.CONFLICT, code, message)

class BillingUnavailableException(code: String, message: String) :
    BillingApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message)
