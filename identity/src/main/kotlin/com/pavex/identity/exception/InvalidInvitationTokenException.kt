package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class InvalidInvitationTokenException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "invalid_invitation_token",
        message = "Invalid or expired invitation token.",
    )
