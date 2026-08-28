package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class PasswordUnchangedException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "password_unchanged",
        message = "New password must be different from the current password.",
    )
