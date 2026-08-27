package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class RoleNotFoundException :
    ApiException(
        status = HttpStatus.NOT_FOUND,
        code = "role_not_found",
        message = "Role was not found.",
    )
