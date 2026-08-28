package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class CannotRemoveOwnAdminRoleException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "cannot_remove_own_admin_role",
        message = "You cannot remove your own ADMIN role.",
    )
