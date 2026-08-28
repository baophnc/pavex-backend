package com.pavex.identity.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
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
}