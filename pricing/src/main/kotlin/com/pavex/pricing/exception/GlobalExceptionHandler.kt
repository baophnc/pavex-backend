package com.pavex.pricing.exception

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(PricingApiException::class)
    fun handlePricing(exception: PricingApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(exception.code, exception.message))

    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun handleValidation(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse("invalid_request", "Request fields are invalid or outside the supported pricing limits."),
        )

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse("rate_plan_changed", "The rate plan changed while you were editing it. Reload and try again."),
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse("pricing_conflict", "The pricing change conflicts with existing rate data."),
        )

    @ExceptionHandler(ArithmeticException::class)
    fun handleArithmetic(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiErrorResponse("pricing_calculation_overflow", "Pricing could not safely calculate this quote."),
        )
}
