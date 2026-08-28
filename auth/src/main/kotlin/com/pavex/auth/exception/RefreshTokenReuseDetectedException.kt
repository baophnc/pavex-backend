package com.pavex.auth.exception

import org.springframework.http.HttpStatus

class RefreshTokenReuseDetectedException :
    ApiException(
        status = HttpStatus.UNAUTHORIZED,
        code = "invalid_refresh_token",
        message = "Refresh token is invalid or expired",
    )
