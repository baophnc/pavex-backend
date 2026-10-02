package com.pavex.notifications.exception

import com.pavex.common.api.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

open class NotificationApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)

class InvalidNotificationRequestException(code: String, message: String) :
    NotificationApiException(HttpStatus.BAD_REQUEST, code, message)

class NotificationForbiddenException(code: String, message: String) :
    NotificationApiException(HttpStatus.FORBIDDEN, code, message)

class NotificationNotFoundException(code: String, message: String) :
    NotificationApiException(HttpStatus.NOT_FOUND, code, message)

class NotificationUnavailableException(code: String, message: String) :
    NotificationApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message)

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NotificationApiException::class)
    fun handle(exception: NotificationApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(exception.code, exception.message))

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleValidation(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(ApiErrorResponse("invalid_request", "Request fields are missing or invalid."))
}
