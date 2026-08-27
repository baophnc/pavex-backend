package com.pavex.identity.exception

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message)


class EmailAlreadyExistsException(
    email: String,
) : ApiException(
    status = HttpStatus.CONFLICT,
    code = "email_already_exists",
    message = "An account with email '$email' already exists",
)