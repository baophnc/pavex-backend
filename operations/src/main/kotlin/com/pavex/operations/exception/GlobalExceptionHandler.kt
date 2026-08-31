package com.pavex.operations.exception

import com.pavex.common.api.ApiErrorResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(OperationsApiException::class)
    fun handleOperations(exception: OperationsApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(exception.code, exception.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(ApiErrorResponse("invalid_request", "Request fields are invalid."))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorResponse("operations_conflict", "The operations change conflicts with existing data."),
        )
}
