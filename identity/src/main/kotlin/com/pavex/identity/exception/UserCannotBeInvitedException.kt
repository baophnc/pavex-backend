package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class UserCannotBeInvitedException :
    ApiException(
        status = HttpStatus.BAD_REQUEST,
        code = "user_cannot_be_invited",
        message = "User cannot be invited.",
    )
