package com.pavex.auth.service

import com.pavex.auth.dto.auth.AuthResponse
import com.pavex.auth.entity.UserAccount
import com.pavex.auth.security.JwtService
import com.pavex.contracts.identity.IdentityAuthContext
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AuthSessionService(

    private val jwtService:
        JwtService,

    private val refreshTokenService:
        RefreshTokenService,
) {

    fun issue(
        account: UserAccount,
        now: Instant,
        clientMetadata:
            ClientMetadata,
        authorization: IdentityAuthContext? = null,
    ): AuthResponse {

        val accessToken =
            if (authorization == null) {
                jwtService.createAccessToken(
                    account,
                    now,
                )
            } else {
                jwtService.createAccessToken(
                    account = account,
                    now = now,
                    authorization = authorization,
                )
            }

        val refreshToken =
            refreshTokenService
                .create(
                    userId =
                        account.id,

                    now =
                        now,

                    clientMetadata =
                        clientMetadata,
                )

        return AuthResponse(
            accessToken =
                accessToken.token,

            refreshToken =
                refreshToken.token,

            expiresIn =
                accessToken.expiresIn,

            passwordChangeRequired =
                account.mustChangePassword,
        )
    }
}
