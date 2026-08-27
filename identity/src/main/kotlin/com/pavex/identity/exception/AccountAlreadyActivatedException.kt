package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class AccountAlreadyActivatedException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "account_already_activated",
        message = "Account has already been activated.",
    )
