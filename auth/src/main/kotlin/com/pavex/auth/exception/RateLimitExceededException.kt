package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class RateLimitExceededException(
    val retryAfterSeconds: Long,
) : ApiException(
    status = HttpStatus.TOO_MANY_REQUESTS,
    code = "rate_limit_exceeded",
    message = "Too many requests. Please try again later.",
)
