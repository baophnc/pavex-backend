package com.pavex.shipment.exception

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ShipmentApiException::class)
    fun handleShipmentApiException(exception: ShipmentApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(exception.code, exception.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Request validation failed."
        return ResponseEntity.badRequest().body(ApiErrorResponse("validation_failed", message))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(ApiErrorResponse("invalid_shipment_request", exception.message ?: "Invalid shipment request."))
}
