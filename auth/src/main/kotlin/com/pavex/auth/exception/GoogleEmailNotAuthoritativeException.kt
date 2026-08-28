package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class GoogleEmailNotAuthoritativeException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "google_email_not_authoritative",
        message =
            "This Google account email cannot be trusted for automatic account creation or linking.",
    )
