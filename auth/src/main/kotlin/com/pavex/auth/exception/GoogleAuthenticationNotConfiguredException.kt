package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class GoogleAuthenticationNotConfiguredException :
    ApiException(
        status = HttpStatus.SERVICE_UNAVAILABLE,
        code = "google_authentication_not_configured",
        message = "Google authentication is not configured.",
    )
