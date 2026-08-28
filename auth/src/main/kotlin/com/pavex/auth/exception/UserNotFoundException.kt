package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class UserNotFoundException :
    ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "user_not_found",
        message = "User was not found.",
    )
