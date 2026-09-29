package com.pavex.partners.exception

import com.pavex.common.api.ApiErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(
            ApiErrorResponse(exception.code, exception.message),
        )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                code = "invalid_request",
                message = "Request fields are invalid or exceed the allowed length.",
            ),
        )

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleUploadTooLarge(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
            ApiErrorResponse(
                code = "support_attachment_invalid",
                message = "Mỗi tệp phải nhỏ hơn 5 MB.",
            ),
        )

    @ExceptionHandler(HttpMessageNotReadableException::class, MissingServletRequestPartException::class)
    fun handleUnreadable(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiErrorResponse(
                code = "invalid_request",
                message = "Request body is missing or malformed.",
            ),
        )

    @ExceptionHandler(IdentityServiceUnavailableException::class)
    fun handleIdentityUnavailable(): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ApiErrorResponse(
                code = "identity_service_unavailable",
                message = "Identity service is temporarily unavailable.",
            ),
        )
}
