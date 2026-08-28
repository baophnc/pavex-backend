package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class InvalidUserProfileException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid_user_profile",
        message =
            "User profile information is invalid.",
    )
