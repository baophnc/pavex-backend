package com.pavex.identity.exception

import org.springframework.http.HttpStatus

class AccountDeletedException :
    ApiException(
        status = HttpStatus.CONFLICT,
        code = "account_deleted",
        message = "Tài khoản đã bị xoá nên không thể thay đổi.",
    )
