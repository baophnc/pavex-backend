package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class AccountAlreadyActivatedException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "account_already_activated",
        message = "Account has already been activated.",
    )
