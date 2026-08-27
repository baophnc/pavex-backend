package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class ActiveSessionNotFoundException :
    ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "active_session_not_found",
        message = "Active session was not found.",
    )
