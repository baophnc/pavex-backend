package com.pavex.pricing.exception

import org.springframework.http.HttpStatus

open class PricingApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(code: String, message: String) :
    PricingApiException(HttpStatus.NOT_FOUND, code, message)

class ConflictException(code: String, message: String) :
    PricingApiException(HttpStatus.CONFLICT, code, message)

class InvalidPricingRequestException(code: String, message: String) :
    PricingApiException(HttpStatus.BAD_REQUEST, code, message)

data class ApiErrorResponse(val code: String, val message: String)
