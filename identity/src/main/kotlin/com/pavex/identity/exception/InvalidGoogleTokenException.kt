package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class InvalidGoogleTokenException :
    ApiException(
        status = HttpStatus.UNAUTHORIZED,
        code = "invalid_google_token",
        message = "Invalid Google ID token.",
    )
