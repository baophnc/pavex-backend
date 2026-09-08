package com.pavex.auth.exception

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    // Handle malformed/missing JSON fields here instead of forwarding to the
    // protected /error endpoint, which can mask a bad request as a JWT 401.
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableMessage(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Request body must be valid JSON with all required fields",
        ).apply {
            title = "invalid_request_body"
            type = URI.create("https://api.pavex.com/problems/invalid_request_body")
            setProperty("code", "invalid_request_body")
        }

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimitExceeded(
        exception: RateLimitExceededException,
    ): ResponseEntity<ProblemDetail> {

        val problem = apiProblem(exception)

        return ResponseEntity
            .status(exception.status)
            .header(
                HttpHeaders.RETRY_AFTER,
                exception.retryAfterSeconds
                    .toString(),
            )
            .body(problem)
    }

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        exception: ApiException,
    ): ProblemDetail =
        apiProblem(exception)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        exception: MethodArgumentNotValidException,
    ): ProblemDetail {

        val errors = exception
            .bindingResult
            .fieldErrors
            .associate { error ->
                error.field to
                    (error.defaultMessage ?: "Invalid value")
            }

        val problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Request validation failed",
        )

        problem.title = "validation_failed"

        problem.type = URI.create(
            "https://api.pavex.com/problems/validation_failed",
        )

        problem.setProperty(
            "code",
            "validation_failed",
        )

        problem.setProperty(
            "errors",
            errors,
        )

        return problem
    }

    private fun apiProblem(
        exception: ApiException,
    ): ProblemDetail {

        val problem = ProblemDetail.forStatusAndDetail(
            exception.status,
            exception.message,
        )

        problem.title = exception.code
        problem.type = URI.create(
            "https://api.pavex.com/problems/${exception.code}",
        )

        problem.setProperty(
            "code",
            exception.code,
        )

        return problem
    }
}
