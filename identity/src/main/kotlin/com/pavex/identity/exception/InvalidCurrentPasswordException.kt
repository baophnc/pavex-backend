package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class InvalidCurrentPasswordException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid_current_password",
        message = "Current password is incorrect.",
    )
