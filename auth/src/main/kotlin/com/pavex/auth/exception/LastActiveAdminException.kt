package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class LastActiveAdminException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "last_active_admin",
        message = "The last active ADMIN cannot be removed.",
    )
