package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class ExternalIdentityLinkRequiredException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "external_identity_link_required",
        message =
            "An account already exists for this email. Sign in using the existing method before linking Google.",
    )
