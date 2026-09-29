package com.pavex.fleet.exception

import com.pavex.common.api.ApiErrorResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

open class FleetApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class InvalidFleetRequestException(code: String, message: String) :
    FleetApiException(HttpStatus.BAD_REQUEST, code, message)

class FleetForbiddenException(code: String, message: String) :
    FleetApiException(HttpStatus.FORBIDDEN, code, message)

class FleetNotFoundException(code: String, message: String) :
    FleetApiException(HttpStatus.NOT_FOUND, code, message)

class FleetConflictException(code: String, message: String) :
    FleetApiException(HttpStatus.CONFLICT, code, message)

class FleetUnavailableException(code: String, message: String) :
    FleetApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(FleetApiException::class)
    fun handleFleet(exception: FleetApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(exception.code, exception.message))

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleValidation(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(ApiErrorResponse("invalid_request", "Request fields are missing or invalid."))

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse("trip_state_changed", "The trip changed while it was being updated. Reload and retry."),
        )

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse("trip_conflict", "The change conflicts with another trip of this driver."),
        )
}
